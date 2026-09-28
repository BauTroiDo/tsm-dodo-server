ArmySupport = {}
local this = ArmySupport;

this.pokeMaxCount = 81;
this.armyTotalScore = 0;
this.playerScore = 0;
this.pokeTime = 0;
this.awardId = 0;
this.pokedIdx = 0;
this.plateId = 2;
this.supportActID = 122;
this.supportMissID = 123;

this.pokeData = {};
this.pokeBonusData = {};

local playerScoreRank = {};
local pokeAwardHistory = {};

function ArmySupport.Init()
  for i = 1, this.pokeMaxCount do
    this.pokeData[i] = {};
    this.pokeData[i].status = 0;
    this.pokeData[i].awardKind = 1;
  end

  this.pokeBonusData = PokeBonusRewardData.GetData();
end

function ArmySupport.ActivityIsOpen(id)
  return activityTimes[id] ~= nil and
    activityTimes[id][1].serverStartTime ~= 0 and
    activityTimes[id][1].serverCloseTime ~= 0 and
    DateTime.Compare(activityTimes[id][1].serverCloseTime, CGTimer.serverTime) >= 0 and
    DateTime.Compare(activityTimes[id][1].serverStartTime, CGTimer.serverTime) <= 0;
end

function ArmySupport.Clear()
  this.Init();

  this.armyTotalScore = 0;
  this.playerScore = 0;
  this.pokeTime = 0;
  this.awardId = 0;
  this.pokedIdx = 0;
  this.plateId = 2;

  playerScoreRank = {};
  pokeAwardHistory = {};
end

function ArmySupport.UpdateRedDot()
  if this.armyTotalScore > 0 then
    local temp = this.GetArmyScores();

    if table.Count(temp) ~= 0 then
      for i = 1, table.Count(temp) do
        if this.armyTotalScore >= temp[i].day then
          if not BitFlag.Get(temp[i].flag) then
            RedDot.Add(ERedDot.ArmySupportInfo);
            break;
          end
        end
      end
    end
  end

  if RoleCount.Get(ERoleCount.ArmySupportPoke) > 0 then
    RedDot.Add(ERedDot.ArmySupportPoke);
  end
end

function ArmySupport.GetArmyScores()
  local temp = {};

  for i = 1, table.Count(loginAwardDatas) do
    if loginAwardDatas[i].group == 39 then
      table.insert(temp, loginAwardDatas[i]);
    end
  end

  return temp;
end

function ArmySupport.GetRank()
  return playerScoreRank;
end

function ArmySupport.GetHistory()
  return pokeAwardHistory;
end

function ArmySupport.SetPlayerScoreRank(data)
  table.Clear(playerScoreRank);

  local count = data:ReadUInt32();

  for i = 1, count do
    local temp = {};
    temp.playerId = data:ReadInt64();
    temp.score = data:ReadUInt32();

    if temp.score > 0 then
      table.insert(playerScoreRank, temp);
    end
  end
end

function ArmySupport.SetHistory(data)
  table.Clear(pokeAwardHistory);

  local count = data:ReadByte();

  for i = 1, count do
    local temp = {};
    temp.name = data:ReadString(data:ReadByte());
    temp.item = data:ReadUInt16();
    temp.quantPlus = data:ReadByte();

    table.insert(pokeAwardHistory, temp);
  end
end

function ArmySupport.PokeImage(hasPoked, isBigAward)
  local sprite = "";
  if hasPoked then
    if isBigAward then
      sprite = "but183";
    else
      sprite = "but026D";
    end
  else
    sprite = "but071D";
  end

  return UI.GetSprite(sprite);
end

function ArmySupport.IsSpecial(id)
  for _, v in pairs(this.pokeBonusData[this.plateId].id) do
    if id == v then
      return true;
    end
  end

  return false;
end

function ArmySupport.GetQuantPlus(id)
  if this.pokeBonusData[this.plateId].quantPlus[id] == nil then return 0 end

  return this.pokeBonusData[this.plateId].quantPlus[id];
end

function ArmySupport.GetAwardTimesPlus(quantPlus)
  local times = 1;
  local plus = 0;

  if quantPlus == 1 then
    times = 2;
  elseif quantPlus == 2 then
    plus = 1;
  elseif quantPlus == 3 then
    plus = 2;
  end

  return times, plus;
end

function ArmySupport.GetQuantPlusText(quantPlus)
  local temp = "";

  if quantPlus > 0 then
    temp = string.Get(24663 + quantPlus);
  end

  return temp;
end

--region protocol

function ArmySupport.SendGetArmySupportReward(index)
  sendBuffer:Clear();
  sendBuffer:WriteByte(index);
  Network.Send(77, 11, sendBuffer);
end

--endregion
