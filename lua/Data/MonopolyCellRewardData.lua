MonopolyRewardData = {}
local this = MonopolyRewardData;
MonopolyRewardData.__index = MonopolyRewardData;

local cellCount = 0;
local roundCount = 0;
local roundRefreshIndex = {};
local roundLevels = {};
local rewardIDDigitSplitIndex = 2;

function MonopolyRewardData.New(reader)
  local self = {};
  setmetatable(self, MonopolyRewardData);
  self.rewardID = reader:ReadUInt16(); --格子ID
  self.round = this.GetRewardIDRound(self.rewardID); --圈數
  self.cellID = this.GetRewardIDCell(self.rewardID);
  self.rewardItem = reader:ReadUInt16(); --獎勵道具
  self.rewardItemCount = reader:ReadUInt16(); --獎勵數量
  self.cellImageType = reader:ReadByte();
  MonopolyRewardData.UpdateCount(self);
  return self.round, self.cellID, self;
end

function MonopolyRewardData.UpdateCount(data)
  cellCount = math.max(cellCount, data.cellID);
  roundCount = math.max(roundCount, data.round);
end

function MonopolyRewardData.CalculateRoundRefreshIndex()
  for index = 0, roundCount do
    if monopolyCellRewardDatas[index] == nil then
      roundRefreshIndex[index] = roundRefreshIndex[index - 1];
    else
      roundRefreshIndex[index] = index;
    end
  end
end

function MonopolyRewardData.CalculateRoundToLevel()
  local level = 0;
  for index = 0, roundCount do
    if monopolyCellRewardDatas[index] == nil then
      roundLevels[index] = roundLevels[index - 1];
    else
      level = level + 1;
      roundLevels[index] = level;
    end
  end
end

function MonopolyRewardData.GetRewardIDRound(rewardID)
  local roundResult = math.floor(rewardID / math.pow(10, rewardIDDigitSplitIndex));
  return roundResult
end

function MonopolyRewardData.GetRewardIDCell(rewardID)
  local cellResult = rewardID % math.pow(10, rewardIDDigitSplitIndex);
  return cellResult
end

function MonopolyRewardData.GetCellCount()
  return cellCount;
end

function MonopolyRewardData.GetRoundCount()
  return roundCount;
end

function MonopolyRewardData.GetRefreshIndex(round)
  return roundRefreshIndex[round];
end

function MonopolyRewardData.GetRoundLevel(round)
  return roundLevels[round];
end
