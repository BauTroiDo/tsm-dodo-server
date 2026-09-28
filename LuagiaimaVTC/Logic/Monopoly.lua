Monopoly = {};
local this = Monopoly;
local EProtocolSubKind = {
  requestProgress = 0,
  rollDice = 1
}

local EMonopolyResult = {
  unknown = 0,
  success = 1,
  notActivityTime = 2,
  diceInsufficient = 3,
  bagFull = 4,
  notTime = 5
}

local progressCheck = true;

local totalCell;
local startingCellIndex = 1;
local bagLeftCount = 2;

local currentRound;
local currentCell;
local tokenProgress;

local updateProgressCallback;
local updateProgressCallbackParameter;
local progressResult;

--region ID
local unknownErrorTextID = 10144;
local notActivityTimeTextID = 21007;
local notOpenTextID = 60065;
local receiveRewardTextID = 23426;
local receiveRewardChatTextID = 21179;
local diceInsufficientTextID = 23425;
local bagTooFullTextID = 23424;
local finishRoundTextID = 23473;
--endregion

--region Roll Dice
function Monopoly.RollDice()
  local rollDiceMessage = this.CheckRollDiceAvailable();
  if rollDiceMessage ~= nil then
    ShowCenterMessage(rollDiceMessage);
    UIMonopoly.SetTokenMoveFlag(false);
    return
  end
  this.RollDiceRequest();
end

function Monopoly.RollDiceRequest()
  --check current progress before rollDice
  progressCheck = true;
  this.SendRequestProgress(this.SendRollDiceRequest);
end

--C:093-001<骰骰子> +指定點數(1)
function Monopoly.SendRollDiceRequest()
  tokenProgress = this.CalculateProgress(currentRound, currentCell);
  sendBuffer:Clear();
  sendBuffer:WriteByte(0);
  Network.Send(93, EProtocolSubKind.rollDice, sendBuffer);
end

--S:093-001<骰骰子>+結果(1)+所在位子(1)
function Monopoly.ReceiveRollDiceResult(data)
  progressCheck = false;
  updateProgressCallback = this.RollDiceSuccess;
  this.ReceiveProgress(data);
  progressCheck = true;
end

function Monopoly.RollDiceSuccess()
  local steps = this.CalculateSteps();
  if steps <= 0 then return end
  UIMonopoly.PlayPushButton();
  UIMonopoly.UpdateDiceCount();
  UIMonopoly.StartDiceEffect(steps);
  Monopoly.MoveToken(steps);
  tokenProgress = Monopoly.UpdateProgress();
end

function Monopoly.CheckRollDiceAvailable()
  if not this.CheckBag() then
    return string.Get(bagTooFullTextID);
  end
  if this.GetDiceCount() <= 0 then
    return string.Get(diceInsufficientTextID);
  end
end

function Monopoly.CheckBag()
  return Item.GetBagLeftCount(EThings.Bag) >= bagLeftCount;
end

function Monopoly.GetDiceCount()
  return RoleCount.Get(ERoleCount.MonopolyDice);
end
--endregion

--region Map
-- for square map only
function Monopoly.EqualSideLengthCoordinate(origin, direction, length)
  local coordinate = {};
  local sideCount = table.Count(direction);
  coordinate[1] = origin;

  for directionIndex = 1, sideCount do
    for sideIndex = 1, length do
      local cellIndex = (directionIndex - 1) * length + sideIndex + 1;
      table.insert(coordinate, coordinate[cellIndex - 1] + direction[directionIndex]);
    end
  end

  if (coordinate[1] == coordinate[table.Count(coordinate)]) then
    table.remove(coordinate);
  end
  return coordinate;
end

function Monopoly.SquareCellCoordinate(length)
  local direction = {};
  direction[1] = Vector2.New(0, 1);
  direction[2] = Vector2.New(1, 0);
  direction[3] = Vector2.New(0, -1);
  direction[4] = Vector2.New(-1, 0);
  local origin = Vector2.zero;
  return this.EqualSideLengthCoordinate(origin, direction, length);
end

function Monopoly.GetMapCoordinates()
  totalCell = MonopolyRewardData.GetCellCount();

  if math.fmod(totalCell, 4) ~= 0 then
    totalCell = totalCell + (4 - math.fmod(totalCell, 4));
  end
  local coordinates = {};
  coordinates = Monopoly.SquareCellCoordinate(totalCell / 4);
  return coordinates;
end

function Monopoly.GetNextCell(cellID)
  return math.fmod(cellID, totalCell) + 1;
end

function Monopoly.SetStartingPoint()
  UIMonopoly.AddCellEvent(startingCellIndex, this.RefreshAllCells, EMonopolyCellEventTrigger.pass);
  UIMonopoly.AddCellEvent(startingCellIndex, this.RoundFinishMessage, EMonopolyCellEventTrigger.pass);
  UIMonopoly.AddCellEvent(startingCellIndex, this.RefreshAllCells, EMonopolyCellEventTrigger.arrive);
  UIMonopoly.AddCellEvent(startingCellIndex, this.RoundFinishMessage, EMonopolyCellEventTrigger.arrive);
  UIMonopoly.AddCellEvent(startingCellIndex, UIMonopoly.UpdateRound, EMonopolyCellEventTrigger.pass);
  UIMonopoly.AddCellEvent(startingCellIndex, UIMonopoly.UpdateRound, EMonopolyCellEventTrigger.arrive);
  UIMonopoly.AddCellEvent(startingCellIndex, UIMonopoly.UpdateLevels, EMonopolyCellEventTrigger.pass);
  UIMonopoly.AddCellEvent(startingCellIndex, UIMonopoly.UpdateLevels, EMonopolyCellEventTrigger.arrive);
end

function Monopoly.CoordinateSort(a, b)
  return a.x + a.y > b.x + b.y;
end
--endregion

--region Progress
--C: 093-000<要求大富翁資料>
function Monopoly.SendRequestProgress(callBack, parameter)
  progressResult = nil;
  progressCheck = true;
  sendBuffer:Clear();
  Network.Send(93, EProtocolSubKind.requestProgress, sendBuffer);
  updateProgressCallback = callBack;
  updateProgressCallbackParameter = parameter;
end

--S: 093-000 <大富翁資料> +結果(1) +所在位子(1)
function Monopoly.ReceiveProgress(data)
  local result = data:ReadByte();
  local message;
  if result == EMonopolyResult.unknown then
    message = string.Get(unknownErrorTextID);
  elseif result == EMonopolyResult.notActivityTime then
    message = string.Get(notActivityTimeTextID);
  elseif result == EMonopolyResult.diceInsufficient then
    message = string.Get(diceInsufficientTextID);
  elseif result == EMonopolyResult.bagFull then
    message = string.Get(bagTooFullTextID);
  elseif result == EMonopolyResult.notTime then
    message = string.Get(notOpenTextID);
  end
  progressResult = message;
  if result ~= EMonopolyResult.success then
    ShowCenterMessage(message);
    updateProgressCallback = nil;
    updateProgressCallbackParameter = nil;
    if result ~= EMonopolyResult.diceInsufficient and result ~= EMonopolyResult.bagFull then
      UIMonopoly.SetTokenMoveFlag(false);
      UIMonopoly.OnClick_Close();
    end
    return
  end

  local cellCheck = currentCell;
  local roundCheck = currentRound;

  currentCell = data:ReadByte();
  currentRound = RoleCount.Get(ERoleCount.MonopolyRound);

  if progressCheck then
    tokenProgress = this.UpdateProgress();
    if roundCheck ~= currentRound or cellCheck ~= currentCell then
      UIMonopoly.UpdateProgress();
    end
  end

  if updateProgressCallback == nil then
    updateProgressCallbackParameter = nil;
    return
  end
  if currentCell == nil and currentRound == nil then
    currentCell = 1;
    currentRound = 0;
  end

  updateProgressCallback(updateProgressCallbackParameter);
  updateProgressCallback = nil;
  updateProgressCallbackParameter = nil;
end

function Monopoly.GetCurrentCell()
  return currentCell;
end

function Monopoly.CalculateProgress(round, cell)
  if totalCell == nil then return end
  return round * totalCell + cell;
end

function Monopoly.CalculateSteps()
  local target = this.CalculateProgress(currentRound, currentCell);
  return target - tokenProgress;
end

function Monopoly.CalculateCell(progress)
  return (math.fmod((progress - 1), totalCell)) + 1;
end

function Monopoly.UpdateProgress()
  return this.CalculateProgress(currentRound, currentCell);
end

function Monopoly.GetProgressResult()
  return progressResult
end
--endregion

--region Token
function Monopoly.GetTokenPathCell(steps)
  --only for map without branch
  if (steps <= 0) then
    return nil
  end
  local tokenCell = this.CalculateCell(tokenProgress);
  local tempCell = UIMonopoly.GetCell(tokenCell);
  local path = Queue.New();
  for _ = 1, steps do
    local nextCell = tempCell.nextCell;
    path:Enqueue(nextCell);
    tempCell = nextCell;
  end
  return path;
end

function Monopoly.MoveToken(steps)
  local path = this.GetTokenPathCell(steps);
  UIMonopoly.SetTokenPath(path, true);
end

function Monopoly.RefreshAllCells()
  local roundIndex = math.min(currentRound, MonopolyRewardData.GetRoundCount());
  local refreshIndex = MonopolyRewardData.GetRefreshIndex(roundIndex);
  if roundIndex == nil or refreshIndex == nil then return end
  for cellIndex = 1, totalCell do
    local rewardData = monopolyCellRewardDatas[refreshIndex][cellIndex];
    if rewardData ~= nil then
      UIMonopoly.SetCellReward(cellIndex, rewardData.rewardItem, rewardData.rewardItemCount);
      UIMonopoly.SetCellImage(cellIndex, rewardData.cellImageType);
    else
      logError("Missing Cell Reward Data");
      UIMonopoly.SetCellReward(cellIndex);
      UIMonopoly.SetCellImage(cellIndex);
    end
  end
end
--endregion

--region Message
function Monopoly.GetReceiveRewardMessage(itemName, itemQuant)
  return string.format(string.Get(receiveRewardTextID), itemName, itemQuant);
end

function Monopoly.GetReceiveRewardChatMessage(itemName, itemQuant)
  return string.format(string.Get(receiveRewardChatTextID), itemName, itemQuant);
end

function Monopoly.ClearRewardMessage(cellID)
  UIMonopoly.GetCell(cellID).rewardMessage = nil;
end

function Monopoly.RoundFinishMessage()
  local message = string.format(string.Get(finishRoundTextID), currentRound);
  ShowCenterMessage(message);
end
--endregion
