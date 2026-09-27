Relationship = {};
Relationship.name = "Relationship";
local this = Relationship;

local conditionTypeId =
{
  WarriorAmount = 1,
  WarriorUpgradeLv = 2,
  WarriorTurn = 3,
  TurnTwoWarriorLv = 4
}

local CONST_PRIORITY_OFFSET = -10000;
local CONST_PLAYER_BONUS_SOURCE_RELATIONSHIP_HUNDREDPERCENT = 1;
local CONST_PLAYER_BONUS_SOURCE_RELATIONSHIP_FIFTYPERCENT = 2;

local lastWarriorCollectionPacketTime = 0;

this.CurrentRelationshipIndex = 0;
this.OwnWarriorDatas = {};
this.CurrentBonusCondition = {};
this.CollectionScrollItemDatas = {};
this.RelationshipBonusScrollItemDatas = {};
this.RelationshipPlayerBonusScrollItemDatas = {};

function Relationship.SortRelationshipData()
  local favorites = Setting.client.relationshipFavoriteWarriors;
  table.Clear(this.CollectionScrollItemDatas);

  for id, data in pairs(relationshipDatas) do
    local newData = {};
    for key, value in pairs(data) do
      newData[key] = value;
    end
    this.CollectionScrollItemDatas[id] = newData;
  end

  for _, relationshipData in pairs(this.CollectionScrollItemDatas) do
    if table.Contains(favorites, relationshipData.mainWarriorId) then
      relationshipData.sortNumber = relationshipData.sortNumber + CONST_PRIORITY_OFFSET;
    end
  end

  table.sort(this.CollectionScrollItemDatas, function(a, b) return a.sortNumber < b.sortNumber end);
end

function Relationship.GetHighestTurnOwnWarriorNpcId(collectWarriorSortId)
  local turnTwoWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[3];
  local turnOneWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[2];
  local turnZeroWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[1];

  if this.OwnWarriorDatas[turnTwoWarriorId] ~= nil then
    return turnTwoWarriorId;
  end

  if this.OwnWarriorDatas[turnOneWarriorId] ~= nil then
    return turnOneWarriorId;
  end

  if this.OwnWarriorDatas[turnZeroWarriorId] ~= nil then
    return turnZeroWarriorId;
  end

  return 0;
end

function Relationship.GetHighestTurnNumber(collectWarriorSortId)
  local turnTwoWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[3];
  local turnOneWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[2];
  local turnZeroWarriorId = collectWarriorSortDatas[collectWarriorSortId].ids[1];

  if this.OwnWarriorDatas[turnTwoWarriorId] ~= nil then
    return 2;
  end

  if this.OwnWarriorDatas[turnOneWarriorId] ~= nil then
    return 1;
  end

  if this.OwnWarriorDatas[turnZeroWarriorId] ~= nil then
    return 0;
  end

  return 0;
end

function Relationship.AddOwnWarriorData(npcId, upgradeLv, turnTwoWarriorLv)
  local data = {};
  data.npcId = npcId;
  data.upgradeLv = upgradeLv;
  data.turnTwoWarriorLv = turnTwoWarriorLv;
  if npcDatas[npcId] ~= nil then
    data.turn = npcDatas[npcId].turn;
  end

  this.OwnWarriorDatas[npcId] = data;
end

function Relationship.GetOwnWarriorData(npcId)
  if this.OwnWarriorDatas[npcId] == nil then
    return nil;
  end

  return this.OwnWarriorDatas[npcId];
end

function Relationship.CleanOwnWarriorData()
  table.Clear(this.OwnWarriorDatas);
end

function Relationship.SetRelationshipBonusScrollItemData()
  table.Clear(this.RelationshipBonusScrollItemDatas);
  local relationshipId = this.CollectionScrollItemDatas[this.CurrentRelationshipIndex].id;

  for bonusId, bonusData in pairs(relationshipBonusDatas[relationshipId]) do
    local title = {};
    title.titleId = bonusId;
    title.contentCount = table.Count(bonusData);
    table.insert(this.RelationshipBonusScrollItemDatas, title);

    local tempTable = {}
    for _, myTable in pairs(bonusData) do
      for _, value in pairs(myTable) do
        local data = {};
        data.serialNumber = value.serialNumber;
        data.conditionType = value.conditionType;
        data.conditionValue = value.conditionValue;
        data.bonusType = value.bonusType;
        data.propertiesId = value.propertiesId;
        data.propertiesValue = value.propertiesValue;
        table.insert(tempTable, data);
      end
    end

    table.sort(tempTable, function(a, b) return a.conditionValue < b.conditionValue end);
    for _, data in pairs(tempTable) do
      table.insert(this.RelationshipBonusScrollItemDatas, data);
    end
  end
end

function Relationship.SetRelationshipPlayerBonusScrollItemData()
  table.Clear(this.RelationshipPlayerBonusScrollItemDatas);
  this.SetRelationshipBonusScrollItemDataWithIndex(CONST_PLAYER_BONUS_SOURCE_RELATIONSHIP_FIFTYPERCENT);
  this.SetRelationshipBonusScrollItemDataWithIndex(CONST_PLAYER_BONUS_SOURCE_RELATIONSHIP_HUNDREDPERCENT);
end

function Relationship.SetRelationshipBonusScrollItemDataWithIndex(bonusSourceIndex)
  local relationshipId = this.CollectionScrollItemDatas[this.CurrentRelationshipIndex].id;
  local tempTable = {};

  if playerBonusDatas[bonusSourceIndex] == nil then
    playerBonusDatas[bonusSourceIndex] = {};
  end

  if playerBonusDatas[bonusSourceIndex][relationshipId] == nil then
    playerBonusDatas[bonusSourceIndex][relationshipId] = {};
  end

  if table.Count(playerBonusDatas[bonusSourceIndex][relationshipId]) ~= 0 then
    local fiftyTitle = {};
    fiftyTitle.titleId = bonusSourceIndex;
    table.insert(this.RelationshipPlayerBonusScrollItemDatas, fiftyTitle);
  end

  for _, playerBonusData in pairs(playerBonusDatas[bonusSourceIndex][relationshipId]) do
    local data = {};
    data.serialNumber = playerBonusData.serialNumber;
    data.bonusSource = playerBonusData.bonusSource;
    data.idInSource = playerBonusData.idInSource;
    data.conditionType = playerBonusData.conditionType;
    data.conditionId = playerBonusData.conditionId;
    data.operator = playerBonusData.operator;
    data.conditionValue = playerBonusData.conditionValue;
    data.bonusType = playerBonusData.bonusType;
    data.bonusAmount = playerBonusData.bonusAmount;
    table.insert(tempTable, data);
  end

  table.sort(tempTable, function(a, b) return a.bonusType < b.bonusType end);
  for _, data in pairs(tempTable) do
    table.insert(this.RelationshipPlayerBonusScrollItemDatas, data);
  end
end

function Relationship.GetRelationshipBonusText(bonusId)
  local tempString = "";

  if bonusId == conditionTypeId.WarriorAmount then
    tempString = string.format(string.Get(24639), this.CurrentBonusCondition.WarriorAmount, this.CurrentBonusCondition.MaxWarriorAmount);
  elseif bonusId == conditionTypeId.WarriorUpgradeLv then
    tempString = string.format(string.Get(24658), this.CurrentBonusCondition.WarriorUpgradeLv, this.CurrentBonusCondition.MaxWarriorUpgradeLv);
  elseif bonusId == conditionTypeId.WarriorTurn then
    tempString = string.format(string.Get(24640), this.CurrentBonusCondition.WarriorTurn, this.CurrentBonusCondition.MaxWarriorTurn);
  elseif bonusId == conditionTypeId.TurnTwoWarriorLv then
    tempString = string.format(string.Get(24692), this.CurrentBonusCondition.TurnTwoWarriorLv, this.CurrentBonusCondition.MaxTurnTwoWarriorLv);
  end

  return tempString;
end

function Relationship.UpdateCurrentBonusCondition()
  this.ResetCurrentBonusCondition();
  this.UpdateCompleteCondition();
  this.UpdateMaxCondition();
  this.GetCompletePercentage();
end

function Relationship.ResetCurrentBonusCondition()
  this.CurrentBonusCondition.WarriorAmount = 0;
  this.CurrentBonusCondition.WarriorTurn = 0;
  this.CurrentBonusCondition.WarriorUpgradeLv = 0;
  this.CurrentBonusCondition.TurnTwoWarriorLv = 0;
  this.CurrentBonusCondition.MaxWarriorAmount = 0;
  this.CurrentBonusCondition.MaxWarriorUpgradeLv = 0;
  this.CurrentBonusCondition.MaxWarriorTurn = 0;
  this.CurrentBonusCondition.MaxTurnTwoWarriorLv = 0;
  this.CurrentBonusCondition.CompletePercentage = 0;
end

function Relationship.UpdateCompleteCondition()
  local relatedWarriorIds = this.CollectionScrollItemDatas[this.CurrentRelationshipIndex].relatedWarriorIds;

  for _, value in pairs(relatedWarriorIds) do
    local highestTurnOwnedWarrior = this.GetHighestTurnOwnWarriorNpcId(value);
    if this.OwnWarriorDatas[highestTurnOwnedWarrior] ~= nil then
      this.CurrentBonusCondition.WarriorAmount    = this.CurrentBonusCondition.WarriorAmount + 1;
      this.CurrentBonusCondition.WarriorTurn      = this.CurrentBonusCondition.WarriorTurn + this.OwnWarriorDatas[highestTurnOwnedWarrior].turn;
      this.CurrentBonusCondition.WarriorUpgradeLv = this.CurrentBonusCondition.WarriorUpgradeLv + this.OwnWarriorDatas[highestTurnOwnedWarrior].upgradeLv;
      this.CurrentBonusCondition.TurnTwoWarriorLv = this.CurrentBonusCondition.TurnTwoWarriorLv + this.OwnWarriorDatas[highestTurnOwnedWarrior].turnTwoWarriorLv;
    end
  end
end

function Relationship.UpdateMaxCondition()
  local relationshipId = this.CollectionScrollItemDatas[this.CurrentRelationshipIndex].id;

  for bonusId, bonusData in pairs(relationshipBonusDatas[relationshipId]) do
    for _, mytable in pairs(bonusData) do
      for _, value in pairs(mytable) do
        if bonusId == conditionTypeId.WarriorAmount and value.conditionValue > this.CurrentBonusCondition.MaxWarriorAmount then
          this.CurrentBonusCondition.MaxWarriorAmount = value.conditionValue;
        elseif bonusId == conditionTypeId.WarriorUpgradeLv and value.conditionValue > this.CurrentBonusCondition.MaxWarriorUpgradeLv then
          this.CurrentBonusCondition.MaxWarriorUpgradeLv = value.conditionValue;
        elseif bonusId == conditionTypeId.WarriorTurn and value.conditionValue > this.CurrentBonusCondition.MaxWarriorTurn then
          this.CurrentBonusCondition.MaxWarriorTurn = value.conditionValue;
        elseif bonusId == conditionTypeId.TurnTwoWarriorLv and value.conditionValue > this.CurrentBonusCondition.MaxTurnTwoWarriorLv then
          this.CurrentBonusCondition.MaxTurnTwoWarriorLv = value.conditionValue;
        end
      end
    end
  end
end

function Relationship.GetCompletePercentage()
  local activeConditionCount = 0;
  local totalConditionCount = 0;
  local relationshipId = this.CollectionScrollItemDatas[this.CurrentRelationshipIndex].id;

  for _, bonusData in pairs(relationshipBonusDatas[relationshipId]) do
    for _, myTable in pairs(bonusData) do
      for _, value in pairs(myTable) do
        totalConditionCount = totalConditionCount + 1;
        if this.CheckCondition(value.conditionType, value.conditionValue) then
          activeConditionCount = activeConditionCount + 1;
        end
      end
    end
  end

  local percentage = math.floor(activeConditionCount / totalConditionCount * 100 + 0.5);
  this.CurrentBonusCondition.CompletePercentage = percentage;
end

function Relationship.CheckCondition(conditionType, conditionValue)
  if conditionType == conditionTypeId.WarriorAmount then
    if this.CurrentBonusCondition.WarriorAmount >= conditionValue then
      return true;
    end
  elseif conditionType == conditionTypeId.WarriorUpgradeLv then
    if this.CurrentBonusCondition.WarriorUpgradeLv >= conditionValue then
      return true;
    end
  elseif conditionType == conditionTypeId.WarriorTurn then
    if this.CurrentBonusCondition.WarriorTurn >= conditionValue then
      return true;
    end
  elseif conditionType == conditionTypeId.TurnTwoWarriorLv then
    if this.CurrentBonusCondition.TurnTwoWarriorLv >= conditionValue then
      return true;
    end
  end

  return false;
end

function Relationship.AddFavorite(warriorId)
  local favorites = Setting.client.relationshipFavoriteWarriors;

  if table.Contains(favorites, warriorId) == false then
    table.insert(favorites, warriorId);
    Setting.Save();
  end
end

function Relationship.RemoveFavorite(warriorId)
  local favorites = Setting.client.relationshipFavoriteWarriors;
  table.RemoveByValue(favorites, warriorId);
  Setting.Save();
end

function Relationship.SendAskOwnWarriorData()
  if this.IsWarriorCollectionPacketAvailable() then
    this.ResetWarriorCollectionPacketTime();
    this.CleanOwnWarriorData();
    Network.Send(112, 1);
  end
end

function Relationship.SetWarriorCollectionPacketTime()
  lastWarriorCollectionPacketTime = CGTimer.serverTime;
end

function Relationship.ResetWarriorCollectionPacketTime()
  lastWarriorCollectionPacketTime = 0;
end

function Relationship.IsWarriorCollectionPacketAvailable()
  if lastWarriorCollectionPacketTime == 0 then
    return true;
  end

  if (CGTimer.serverTime - lastWarriorCollectionPacketTime).TotalSeconds > 60 then
    return true;
  else
    return false;
  end
end

function Relationship.Clear()
  Relationship.CleanOwnWarriorData();
  Relationship.ResetWarriorCollectionPacketTime();
end
