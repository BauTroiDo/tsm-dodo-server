EMissionType = {
  Complete = 1, --完成條件兌換
  Exchange = 2  --用物品兌換
}

EMissionOperator = {
  None = 0,
  Equal = 1,
  Bigger = 2,
  BigEqual = 3,
  Smaller = 4,
  SmallEqual = 5,
  NotEqual = 6
}

EModelObj = {
  Enter = 1,
  Activity = 2
}

ESpecialActivity = {
  Bingo = 1,
  Monopoly = 2,
  LeaderBoard = 3,
}

EMissionConditionType = {
  Exchange = 1,
  AccumulatedRecharge = 2,
  Recharge90 = 3,
  Recharge150 = 4,
  Recharge300 = 5,
  Recharge750 = 6,
  Recharge1490 = 7,
  Recharge2990 = 8,
  WarriorGacha_M = 9,
  CardGacha_M = 10,
  MysteriousGacha_M = 11,
  UniqueBloodGacha_M = 12,
  FurnitureGacha_M = 13,
  HighFurnitureGacha_M = 14,
  ConsumeHappyCoin = 15,
  ConsumePokeCoin = 16,
  ConsumeSlot = 17,

  WarriorGacha_N_Purchase = 18,
  WarriorGacha_N = 19,
  CardGacha_N = 20,
  Appearance_N_Purchase = 21,
  SpecializedWeapon_N_Limit = 22,
  SpecializedWeapon_N = 23,
  FurnitureGacha_N_Purchase = 24,
  FurnitureGacha_N = 25,
  HighFurnitureGacha_N = 26,
  SoulGacha_N = 27,
  SpecialSoulGacha_N = 28,
  KeepLoginDays = 29,
  AccumulatedLoginDays = 30,

  SpecifiedDateLogin = 31,

  ConsumeGold = 32,
  ConsumePurchase = 33,
  FightCount = 34,
  SingleDungeonCount = 35,
  PartyDungeonCount = 36,
  DispatchCount = 70,
  ItemCraftCount = 90,
  EquipElementCount = 91,
  EquipRefineryCount = 92,
  EquipProofCount = 93,
  EquipAffixCount = 94,

  BingoRandomReward = 151,
  BingoReward = 152,
  BingoFlippedCount = 153,
  BingoAchieveTimes = 154,
  BingoChangeTimes = 155,

  MonopolyDiceThrowTimes = 156,
  MonopolyIndicatorMoveDistance = 157,
  MonopolyCycleCounts = 158,
  MonopolyReward = 159,

  LeaderBoardSimpleCoins = 160,
  LeaderBoardSimple = 161,
}

--const
local CONST_ONE_HOUR = 60 * 60;
local CONST_COIN_ITEM_INDEX = 29951;
local CONST_COIN_ITEM_INDEX_END = 30000;

ActivityModel = {}
local this = ActivityModel;
this.MISSION_INDEX = 1000;

--var
this.showObj = EModelObj.Enter;
this.openUI = false;
this.openActivityId = 0;
this.hasActiveActivity = false;
this.nearestActivity = nil;

this.ActivityData = {};
this.ActivityCoin = {};
this.PlayerAwardCount = {};

local isShowIcon = false; --狂歡按鈕顯示
local incomingActivityTime = math.huge;
local isInitBingo = false;
local secondPage = 2;

function ActivityModel.GetData(data)
  local dataTemp = {};

  dataTemp.activityId = data:ReadByte();--項目編號
  dataTemp.isOpen = data:ReadByte();
  dataTemp.kind = data:ReadByte();--活動種類
  dataTemp.picture = data:ReadByte();--圖片編號
  dataTemp.rolePicture = data:ReadByte();--角色圖號
  dataTemp.sort = data:ReadByte();
  dataTemp.hintStringId = data:ReadUInt32();--說明
  dataTemp.title = data:ReadString(data:ReadByte());
  dataTemp.description = data:ReadString(data:ReadByte());
  dataTemp.showDot = false;
  dataTemp.isCurrentActivity = false;

  dataTemp.pageCount = data:ReadUInt32();
  dataTemp.page = {};

  for i = 1, dataTemp.pageCount do
    local page = data:ReadByte();
    dataTemp.page[page] = {};
    dataTemp.page[page].missions = {};
    dataTemp.page[page].title = data:ReadString(data:ReadByte());
    dataTemp.page[page].description = data:ReadString(data:ReadByte());
    dataTemp.page[page].showDot = false;

    if dataTemp.page[page].title == '' then
      dataTemp.page[page] = nil;
    elseif dataTemp.page[page].title == '0' then
      dataTemp.page[page].title = '';
    elseif dataTemp.kind == LeaderboardSimpleData.ActivityKind and page == 3 then
      dataTemp.page[page].title = ''
    end
  end

  dataTemp.startTime = DateTime.FromOADate(data:ReadDouble());
  dataTemp.endTime = DateTime.FromOADate(data:ReadDouble());
  dataTemp.startShowTime = DateTime.FromOADate(data:ReadDouble());
  dataTemp.endShowTime = DateTime.FromOADate(data:ReadDouble());

  if DateTime.Compare(CGTimer.serverTime, dataTemp.endShowTime) < 0 and
      dataTemp.isOpen ~= 0 and
      ( DateTime.Compare(CGTimer.serverTime, dataTemp.startShowTime) >= 0 or
      DateTime.Compare(CGTimer.serverTime, dataTemp.startTime) >= 0 ) then

    dataTemp.isCurrentActivity = true;
    dataTemp.isEnd = false;
    this.hasActiveActivity = true;
    RedDot.Add(ERedDot.ActivityModelHasAct);
  end

  dataTemp.coinCount = data:ReadUInt32();
  dataTemp.coin = {};

  for i = 1, dataTemp.coinCount do
    local coinId = data:ReadUInt16();
    dataTemp.coin[i] = {};
    dataTemp.coin[i].data = this.ActivityCoin[coinId];
    dataTemp.coin[i].id = coinId;
  end

  dataTemp.activityCount = data:ReadUInt32();
  dataTemp.missionsWithNoPage = {};

  for i = 1, dataTemp.activityCount do
    local mission = {};
    mission.id = data:ReadUInt32();
    mission.order = data:ReadUInt16();
    mission.conditionId = data:ReadByte();
    mission.type = this.GetMissionType(mission.conditionId);
    mission.operator = data:ReadByte(); --運算子
    mission.conditionCount = data:ReadUInt32(); --數量值
    mission.getLimit = data:ReadByte(); --可領取次數
    mission.status = ESortOrder.Processing;
    mission.itemChange = {};
    mission.itemCost = {};

    for j = 1, 5 do
      mission.itemChange[j] = {};
      mission.itemChange[j].kind = data:ReadByte();
      mission.itemChange[j].itemId = data:ReadUInt16();
      mission.itemChange[j].quant = data:ReadUInt32();
    end

    for j = 1, 2 do
      mission.itemCost[j] = {};
      mission.itemCost[j].kind = data:ReadByte();
      mission.itemCost[j].itemId = data:ReadUInt16();
      mission.itemCost[j].quant = data:ReadUInt32();
    end

    mission.discount = data:ReadByte();
    local page = this.GetPage(dataTemp.activityId, mission.id);
    dataTemp.pageCount = table.Count(dataTemp.page);
    if page > 0 and dataTemp.pageCount > 0 then
      if dataTemp.page[page] then
        table.insert(dataTemp.page[page].missions, mission);
      end
    else
      table.insert(dataTemp.missionsWithNoPage, mission);
    end

    this.PlayerAwardCount[mission.id] = {};
    this.PlayerAwardCount[mission.id].complete = 0; --數量值
    this.PlayerAwardCount[mission.id].get = 0;      --領取次數
    this.PlayerAwardCount[mission.id].mission = mission;
  end

  this.ActivityData[dataTemp.activityId] = dataTemp;
end

function ActivityModel.SortMission(missionList)
  local final = {};
  local first = {};
  local second = {};
  local third = {};

  for _, v in pairs(missionList) do
    this.CheckMissionStatus(v);
    if v.status == ESortOrder.Available then
      table.insert(first, v);
    elseif v.status == ESortOrder.Processing then
      table.insert(second, v);
    elseif v.status == ESortOrder.Complete then
      table.insert(third, v);
    end
  end

  if table.Count(first) > 1 then
    table.sort(first, function(a, b)
      if a.order == b.order then
        return a.id < b.id
      else
        return a.order < b.order
      end
    end)
  end

  if table.Count(second) > 1 then
    table.sort(second, function(a, b)
      if a.order == b.order then
        return a.id < b.id
      else
        return a.order < b.order
      end
    end)
  end

  if table.Count(third) > 1 then
    table.sort(third, function(a, b)
      if a.order == b.order then
        return a.id < b.id
      else
        return a.order < b.order
      end
    end)
  end

  for _, v in pairs(first) do
    table.insert(final, v);
  end

  for _, v in pairs(second) do
    table.insert(final, v);
  end

  for _, v in pairs(third) do
    table.insert(final, v);
  end

  return final;
end

function ActivityModel.GetCoinData(data)
  local dataTemp = {};

  dataTemp.id = data:ReadUInt16();
  dataTemp.name = data:ReadString(data:ReadByte());
  dataTemp.itemId = data:ReadUInt16();
  if(dataTemp.itemId >= CONST_COIN_ITEM_INDEX and dataTemp.itemId <= CONST_COIN_ITEM_INDEX_END) then
    itemDatas[dataTemp.itemId].name = dataTemp.name;
  end
  dataTemp.quant = 0;

  this.ActivityCoin[dataTemp.id] = dataTemp;
end

function ActivityModel.GetAwardCount(data)
  local id = data:ReadUInt32();

  if this.PlayerAwardCount[id] == nil then
    this.PlayerAwardCount[id] = {};
    this.PlayerAwardCount[id].complete = 0;
    this.PlayerAwardCount[id].get = 0;
    this.PlayerAwardCount[id].mission = nil;
  end

  this.PlayerAwardCount[id].get = data:ReadUInt32();
  this.CheckMissionStatus(this.PlayerAwardCount[id].mission);
end

function ActivityModel.GetFinishCount(data)
  local id = data:ReadUInt32();

  if this.PlayerAwardCount[id] == nil then
    this.PlayerAwardCount[id] = {};
    this.PlayerAwardCount[id].complete = 0;
    this.PlayerAwardCount[id].get = 0;
    this.PlayerAwardCount[id].mission = nil;
  end

  this.PlayerAwardCount[id].complete = data:ReadUInt32();
  this.CheckMissionStatus(this.PlayerAwardCount[id].mission);
end

function ActivityModel.GetPage(activityId, missionId)
  local page = 0;
  page = math.floor((missionId - activityId * 1000) / 100);

  return page;
end

function ActivityModel.GetMissionType(conditionId)
  if conditionId == EMissionConditionType.Exchange then
    return EMissionType.Exchange;
  else
    return EMissionType.Complete;
  end
end

function ActivityModel.GetActivityCoinOrItem(kind, itemId)
  if kind == 1 then
    if this.ActivityCoin[itemId] == nil then
      logError("!!!活動貨幣"..itemId.."無資料!!!");
      ShowCenterMessage("!!!活動貨幣"..itemId.."無資料!!!");
      return itemId;
    end

    local coinItemId = CONST_COIN_ITEM_INDEX;
    for i = 1, table.Count(this.ActivityCoin) do
      local j = i - 1;
      if itemDatas[CONST_COIN_ITEM_INDEX].price == 0 then
        UIActivityModel.UpdateCoin(true);
      end

      if itemId == itemDatas[CONST_COIN_ITEM_INDEX + j].price then --這邊借用price當作CoinId
        coinItemId = CONST_COIN_ITEM_INDEX + j;
        break;
      end
    end
    return coinItemId;
  else
    return itemId;
  end
end

function ActivityModel.GetDiscountInfo(discount)
  local text = "";
  local sprite = UI.GetSprite("com098");

  if discount > 0 and discount < 100 then
    text = string.format(string.Get(24896), discount);
  end

  if discount == 101 then
    sprite = UI.GetSprite("com096");
  elseif discount == 102 then
    sprite = UI.GetSprite("com097");
  elseif discount == 103 then
    sprite = UI.GetSprite("com098");
  end

  return text, sprite;
end

function ActivityModel.CheckMissionStatus(mission)
  if mission == nil then return end
  local activityIdx = math.floor(mission.id / 1000);
  if not this.ActivityData[activityIdx].isCurrentActivity then return end

  if mission.type == EMissionType.Exchange then
    local firstCheck = true;
    local secondCheck = true;

    for i = 1, 2 do
      if mission.itemCost[i].itemId ~= 0 then
        if mission.itemCost[i].kind == 1 then
          if this.ActivityCoin[mission.itemCost[i].itemId].quant < mission.itemCost[i].quant then
            if i == 1 then
              firstCheck = false;
            else
              secondCheck = false;
            end
          end
        elseif mission.itemCost[i].kind == 2 then
          local haveQuant = Item.GetItemCount(mission.itemCost[i].itemId);

          if haveQuant < mission.itemCost[i].quant then
            if i == 1 then
              firstCheck = false;
            else
              secondCheck = false;
            end
          end
        end
      else
        if i == 1 then
          firstCheck = false;
        else
          secondCheck = true;
        end
      end
    end

    if mission.getLimit == 0 or this.PlayerAwardCount[mission.id].get < mission.getLimit then
      if secondCheck and firstCheck then
        mission.status = ESortOrder.Available;
      else
        mission.status = ESortOrder.Processing;
      end
    else
      mission.status = ESortOrder.Complete;
    end

  elseif mission.type == EMissionType.Complete then
    local check = false;
    local getCount = 0;

    if mission.getLimit > 1 then
      getCount = this.PlayerAwardCount[mission.id].get;
    else
      getCount = 1;
    end

    if mission.conditionId == EMissionConditionType.SpecifiedDateLogin then
      check = this.PlayerAwardCount[mission.id].complete > 0;
	elseif (mission.conditionId >= EMissionConditionType.Recharge90 and mission.conditionId <= EMissionConditionType.Recharge2990) or (mission.getLimit <= 1) then
      check = this.GetResult(mission.conditionCount * getCount, mission.operator, this.PlayerAwardCount[mission.id].complete);
    elseif mission.getLimit > 1 then
      getCount = getCount + 1;
      check = this.GetResult(mission.conditionCount * getCount, mission.operator, this.PlayerAwardCount[mission.id].complete);
    end

    if check then
      if this.PlayerAwardCount[mission.id].get >= mission.getLimit then
        if mission.getLimit == 0 then
          mission.status = ESortOrder.Available;
        else
          mission.status = ESortOrder.Complete;
        end
      else
        if this.PlayerAwardCount[mission.id].complete - this.PlayerAwardCount[mission.id].get  > 0 then
          mission.status = ESortOrder.Available;
        else
          if this.PlayerAwardCount[mission.id].complete >= mission.getLimit then
            mission.status = ESortOrder.Complete;
          else
            mission.status = ESortOrder.Processing;
          end
        end
      end
    else
      mission.status = ESortOrder.Processing;
    end
  end

  local page = 0;
  local actId = 0;

  --任務ID = 活動ID*1000 + 分頁(1~9*100) + 流水號(1~99)

  actId = math.floor(mission.id / 1000);
  page = math.floor((mission.id - actId * 1000) / 100);

  this.CheckPageDot(actId, page);
end

function ActivityModel.CheckPageDot(activityId, page)
  local tempPage = this.ActivityData[activityId].page[page];
  local tempMissions = this.ActivityData[activityId].missionsWithNoPage;
  local missionCount = table.Count(tempMissions);
  local hasTrue = false;
  local temp;

  if tempPage == nil and missionCount == 0 then return end;

  if missionCount == 0 then
    temp = tempPage.missions;
  else
    temp = this.ActivityData[activityId].missionsWithNoPage;
  end

  for _, v in pairs(temp) do
    if v.status == ESortOrder.Available then
      hasTrue = true;
      break;
    end
  end

  if missionCount == 0 then
    tempPage.showDot = hasTrue;
  else
    this.ActivityData[activityId].showDot = hasTrue;
  end
end

function ActivityModel.GetPageAndActivityRedDot(activityId)
  local temp = this.ActivityData[activityId];
  local hasShow = false;

  for pageIndex = 0, temp.pageCount do
    local page = (pageIndex == 0 and temp.missionsWithNoPage) or temp.page[pageIndex].missions;

    for _, mission in pairs(page) do
      this.CheckMissionStatus(mission);
    end

    if pageIndex == 0 then
      hasShow = temp.showDot;
    else
      hasShow = temp.page[pageIndex].showDot;
      if hasShow then break end
    end
  end

  temp.showDot = hasShow;
end

function ActivityModel.GetStatusString(mission)
  local result = "ERROR";

  if mission.status == ESortOrder.Processing then
    local activityIdx = math.floor(mission.id / 1000);
    local status, _ =this.GetActivityStatus(activityIdx);
    if status == EFestivalStatus.ActivityEnd then
      result = string.Get(20377); --活動結束
    else
      result = string.Get(21060); --進行中
    end
  elseif mission.status == ESortOrder.Available then
    result = "";
  elseif mission.status == ESortOrder.Complete then
    result = string.Get(21061); --已完成
  end

  if Define.IsDebugMode() and not UIDebug.RecordMode then
    if mission.type == EMissionType.Complete and mission.conditionId ~= EMissionConditionType.SpecifiedDateLogin then
      -- 方便測試用的拼接，後續若完全確定不需要再Debug相關資料可以完全刪除
      -- result = string.Concat(string.Concat(this.PlayerAwardCount[mission.id].complete, "/", mission.conditionCount), "\n", result, "\n");
      result = string.Concat(" ", "\n", result, "\n");
    end
  end

  return result;
end

function ActivityModel.GetResult(conditionCount, operator, playerCount)
  if operator == EMissionOperator.Equal then
    return playerCount == conditionCount;
  elseif operator == EMissionOperator.Bigger then
    return playerCount > conditionCount;
  elseif operator == EMissionOperator.BigEqual then
    return playerCount >= conditionCount;
  elseif operator == EMissionOperator.Smaller then
    return playerCount < conditionCount;
  elseif operator == EMissionOperator.SmallEqual then
    return playerCount <= conditionCount;
  elseif operator == EMissionOperator.NotEqual then
    return playerCount ~= conditionCount;
  end

  --logError("!!!運算子為0!!!")
  return false;
end

function ActivityModel.GetMissionTitle(mission)
  local data = activityConditionDatas[mission.conditionId];

  if data == nil then
    logError("!!!activityCondition ID "..mission.conditionId.." no data!!!");
    ShowCenterMessage("!!!activityCondition ID "..mission.conditionId.." no data!!!");
    return "ERROR";
  end

  local result = "";

  -- 任務標題字串增補點 (%d)
  if (mission.conditionId == EMissionConditionType.AccumulatedRecharge) or
    ((mission.conditionId >= EMissionConditionType.WarriorGacha_M and mission.conditionId <= EMissionConditionType.PartyDungeonCount) and (mission.conditionId ~= EMissionConditionType.SpecifiedDateLogin)) or
    (mission.conditionId >= EMissionConditionType.ItemCraftCount and mission.conditionId <= EMissionConditionType.EquipAffixCount) or
    (mission.conditionId == EMissionConditionType.DispatchCount) or
    (mission.conditionId >= EMissionConditionType.BingoFlippedCount and mission.conditionId <= EMissionConditionType.MonopolyCycleCounts)
    then
    result = string.format(string.Get(data.stringId), mission.conditionCount);
  elseif mission.conditionId == EMissionConditionType.Exchange then
    if mission.itemChange[1].kind == 1 then
      result = this.ActivityCoin[mission.itemChange[1].itemId].name;
    elseif mission.itemChange[1].kind == 2 then
      result = itemDatas[mission.itemChange[1].itemId].name;
    end
  elseif mission.conditionId == EMissionConditionType.SpecifiedDateLogin then
    local temp = mission.conditionCount;
    local month, day = string.match(temp, "(%d%d)(%d%d)$");
    result = string.format(string.Get(data.stringId), month, day);

  -- 任務標題字串增補點 (%s %d)
  elseif mission.conditionId == EMissionConditionType.LeaderBoardSimpleCoins then
    result = string.format(string.Get(data.stringId), this.ActivityData[this.openActivityId].coin[1].data.name, mission.conditionCount)
  else
    result = string.Get(data.stringId);
  end

  if Define.IsDebugMode() and not UIDebug.RecordMode then
    result = string.Concat(result, " ", mission.id);
  end

  return result;
end

function ActivityModel.GetCompleteCount(mission)
  if mission.type == EMissionType.Complete then
    if ((mission.conditionId >= EMissionConditionType.WarriorGacha_M and mission.conditionId <= EMissionConditionType.PartyDungeonCount) and (mission.conditionId ~= EMissionConditionType.SpecifiedDateLogin)) or
      (mission.conditionId >= EMissionConditionType.ItemCraftCount and mission.conditionId <= EMissionConditionType.EquipAffixCount) or
      (mission.conditionId == EMissionConditionType.DispatchCount) or (mission.conditionId == EMissionConditionType.AccumulatedRecharge) or
      (mission.conditionId >= EMissionConditionType.BingoFlippedCount and mission.conditionId <= EMissionConditionType.LeaderBoardSimple)
      then
      return string.Concat(this.PlayerAwardCount[mission.id].complete, "/", mission.conditionCount);
    end
  end

  return "";
end

function ActivityModel.GetActivityStatus(activityId)
  if this.ActivityData[activityId].isOpen == 0 then
    return EFestivalStatus.Close, string.Get(20377);
  elseif DateTime.Compare(CGTimer.serverTime, this.ActivityData[activityId].startTime) < 0 then
    local leftTime = DateTime.Subtract(this.ActivityData[activityId].startTime, CGTimer.serverTime);
    return EFestivalStatus.Upcoming, string.Concat(string.Get(24878), string.GetSecondText(math.ceil(leftTime.TotalSeconds)));
  elseif DateTime.Compare(CGTimer.serverTime, this.ActivityData[activityId].endTime) < 0 then
    local leftTime = DateTime.Subtract(this.ActivityData[activityId].endTime, CGTimer.serverTime);
    return EFestivalStatus.Open, string.Concat(string.Get(24877), string.GetSecondText(math.ceil(leftTime.TotalSeconds)));
  elseif DateTime.Compare(CGTimer.serverTime, this.ActivityData[activityId].endShowTime) < 0 then
    local leftTime = DateTime.Subtract(this.ActivityData[activityId].endShowTime, CGTimer.serverTime);
    return EFestivalStatus.ActivityEnd, string.Concat(string.Get(24879), string.GetSecondText(math.ceil(leftTime.TotalSeconds)));
  else
    return EFestivalStatus.Close, string.Get(20377);
  end
end

function ActivityModel.GetActivityStatusString(status)
  if status == EFestivalStatus.Upcoming then
    return string.Get(20376);
  elseif status == EFestivalStatus.Open then
    return string.Get(21060);
  elseif status == EFestivalStatus.ActivityEnd then
    return string.Get(20377);
  end

  return "";
end

function ActivityModel.SendGetAward(missionId, count)
  sendBuffer:Clear();
  sendBuffer:WriteUInt32(missionId);
  sendBuffer:WriteUInt32(count);
  Network.Send(124, 3, sendBuffer);
end

--[[
0:成功 1:無此活動 2:未開放領獎 3:太頻繁 4:找不到任務 5:超過領取次數
6:背包空間不足 7:條件不符 8:消耗道具數量不足 9:扣活動貨幣/物品失敗
255:活動未開放/功能未開放
]]
function ActivityModel.ReceiveResult(result)
  if result == 0 then
  elseif result == 1 then
  elseif result == 2 then
  elseif result == 3 then
    ShowCenterMessage(string.Get(22841));
  elseif result == 4 then
  elseif result == 5 then
  elseif result == 6 then
    ShowCenterMessage(string.Get(80359));
  elseif result == 7 then
  elseif result == 8 then
    ShowCenterMessage(string.Get(24591));
  elseif result == 9 then
    ShowCenterMessage(string.Get(10143));
  elseif result == 255 then
    ShowCenterMessage(string.Get(24507));
  end

  if result ~= 0 then
    logError(result);
  end
end

function ActivityModel.Clear()
  if CGTimer.ContainsListener(UIActivityModel.UpdateEnterTime) then
    CGTimer.RemoveListener(UIActivityModel.UpdateEnterTime, 0, true);
  end

  if CGTimer.ContainsListener(UIActivityModel.UpdateActivityTime) then
    CGTimer.RemoveListener(UIActivityModel.UpdateActivityTime, 0, true);
  end

  if CGTimer.ContainsListener(this.FindNearestActivity) then
    CGTimer.RemoveListener(this.FindNearestActivity);
  end

  if CGTimer.ContainsListener(UIActivityModel.UpdateRedDot) then
    CGTimer.RemoveListener(UIActivityModel.UpdateRedDot, 0, true);
  end

  CGTimer.RemoveFunctionDelay(this.CheckActivityStartTime);
  this.hasActiveActivity = false;
  this.nearestActivity = nil;
  isShowIcon = false;
  incomingActivityTime = math.huge;

  table.Clear(this.ActivityData);
  table.Clear(this.ActivityCoin);
  table.Clear(this.PlayerAwardCount);

  for k, v in pairs(festivalInfos) do
    if v.type == 2 then
      table.remove(festivalInfos, k);
    end
  end
end

function ActivityModel.CheckHasActiveActivity()
  this.hasActiveActivity = false;
  local nowActivity = {};
  for  _, v in pairs(ActivityModel.ActivityData) do
    local startInterval = DateTime.Compare(CGTimer.serverTime, v.startShowTime)

    if DateTime.Compare(CGTimer.serverTime, v.endShowTime) < 0 and startInterval >= 0 then
      if v.isOpen ~= 0 then
        v.isCurrentActivity = true;
        this.hasActiveActivity = true;
        table.insert(nowActivity, v);
      end
    else
      v.isCurrentActivity = false;
    end
  end

  return this.hasActiveActivity, nowActivity
end

function ActivityModel.FindNearestActivity()
  for _, v in pairs(this.ActivityData) do
    local diffTime = DateTime.Subtract(v.startShowTime, CGTimer.serverTime).TotalSeconds;
    if diffTime > 0 then
      if diffTime < incomingActivityTime then
        incomingActivityTime = diffTime;
        this.nearestActivity = v;
      end
    end
  end

  if this.nearestActivity ~= nil then
    if incomingActivityTime > CONST_ONE_HOUR then
      if DateTime.Compare(CGTimer.serverTime, this.nearestActivity.startShowTime) < 0 then
        if not CGTimer.ContainsListener(this.FindNearestActivity) then
          CGTimer.AddListener(this.FindNearestActivity, CONST_ONE_HOUR);
        end
      end
    else
      if not isShowIcon and DateTime.Compare(CGTimer.serverTime, this.nearestActivity.startShowTime) < 0 then
        CGTimer.DoFunctionDelay(DateTime.Subtract(this.nearestActivity.startShowTime, CGTimer.serverTime).TotalSeconds, this.CheckActivityStartTime);
      end
    end
  end
end

function ActivityModel.CheckActivityStartTime()
  if not isShowIcon then
    this.CheckHasActiveActivity();
    Function.UpdateVisible();
    RedDot.Add(ERedDot.ActivityModelHasAct);

    if CGTimer.ContainsListener(this.FindNearestActivity) then
      CGTimer.RemoveListener(this.FindNearestActivity);
    end

    isShowIcon = true;
  end
end

function ActivityModel.CheckOnClickSpecialACtivity(parameter)
  if this.CheckCurrentSpecialActivity(ESpecialActivity.Bingo) then
    UIBingo.SetBingo(parameter);
  elseif this.CheckCurrentSpecialActivity(ESpecialActivity.Monopoly) then
    UIActivityMonopoly.SetMonopoly(parameter);
  elseif this.CheckCurrentSpecialActivity(ESpecialActivity.LeaderBoard) then
    if parameter == secondPage then
      UIActivityModel.SetScrollMask(true);
      UI.Open(UILeaderboardSimple, EBoardKind.ActivityModel, this.openActivityId);
    else
      UIActivityModel.SetScrollMask(false);
      UI.Close(UILeaderboardSimple);
    end
  end
end

function ActivityModel.CheckCurrentSpecialActivity(SpecialActivity)
  local kind = this.ActivityData[this.openActivityId].kind
  if SpecialActivity == ESpecialActivity.Bingo then
    if kind == EBingoActKind.fiveSquareMultiBar or kind == EBingoActKind.fiveSquareSingleBar then return true end;
  elseif SpecialActivity == ESpecialActivity.Monopoly then
    if kind == UIActivityMonopoly.EActivityMonopoly.singleBar or kind == UIActivityMonopoly.EActivityMonopoly.multipleBar then return true end;
  elseif SpecialActivity == ESpecialActivity.LeaderBoard then
    if kind == LeaderboardSimpleData.ActivityKind then return true end;
  end
  return false;
end