UIActivityModel = {}
local this = UIActivityModel;

this.name = "UIActivityModel";
this.uiController = nil;

--ui
local gameObject_Enter;
local gameObject_Activity;
local gameObject_PageRoot;
local gameObject_IconRoot;
local gameObject_UIBingo;
local gameObject_Monoply;

local text_Title;
local text_Description;
local text_Time;
local text_Name;

local image_ScrollMask;
local rawImage_Tachie;

local canvasGroup_Image_ScrollRectBG;

local scrollContent_Page;
local scrollItems_Page = {};
local scrollContent_Exchange;
local scrollItems_Exchange = {};
local scrollContent_Festival;
local scrollItems_Festival = {};

local showIcons = {};

local page = 1;

local nowActivity = {};
local activityData = {};
local missionsData = {};
local pageData = {};

local isSendRefresh = false; --重刷新是否冷卻完畢
local CONST_COIN_ITEM_ID = 29951;

function UIActivityModel.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  gameObject_PageRoot = uiController:FindGameObject("Page");
  gameObject_Enter = uiController:FindGameObject("Enter");
  gameObject_Activity = uiController:FindGameObject("Activity");
  gameObject_UIBingo = uiController:FindGameObject("UI_Bingo5X5");
  gameObject_Monoply = uiController:FindGameObject("UI_ActivityMonopoly");

  text_Title = uiController:FindText("Text_Title");
  text_Time = uiController:FindText("Text_Time");
  text_Description = uiController:FindText("Text_Description");
  text_Name = uiController:FindText("Text_Name"):GetComponent("Text");

  image_ScrollMask = uiController:FindImage("Image_Mask");
  rawImage_Tachie = uiController:FindRawImage("ProductPic");

  canvasGroup_Image_ScrollRectBG = uiController:FindImage("Image_ScrollRectBG"):GetComponent("CanvasGroup");

  gameObject_IconRoot = uiController:FindGameObject("IconRoot");

  for i = 1, 2 do
    showIcons[i] = {};
    showIcons[i].gameObject = gameObject_IconRoot.transform:Find(string.Concat("Image_Icon (", i, ")")).gameObject;
    showIcons[i].icon = UIItemController.New(showIcons[i].gameObject.transform);
    showIcons[i].text_Quant = showIcons[i].gameObject.transform:Find("Text_Quant"):GetComponent("Text");
  end

  scrollContent_Festival = uiController:FindScrollContent("ScrollContent_Festival");
  scrollContent_Festival.onInitialize = this.OnInitialize_ScrollContent_Festival;
  scrollContent_Festival.onItemChange = this.OnItemChange_ScrollContent_Festival;
  scrollContent_Festival:Initialize(1);

  scrollContent_Page = uiController:FindScrollContent("ScrollContent_Page");
  scrollContent_Page.onInitialize = this.OnInitialize_ScrollContent_Page;
  scrollContent_Page.onItemChange = this.OnItemChange_ScrollContent_Page;
  scrollContent_Page:Initialize(1);

  scrollContent_Exchange = uiController:FindScrollContent("ScrollContent_Exchange");
  scrollContent_Exchange.onInitialize = this.OnInitialize_ScrollContent_Exchange;
  scrollContent_Exchange.onItemChange = this.OnItemChange_ScrollContent_Exchange;
  scrollContent_Exchange:Initialize(1);

  image_ScrollMask.enabled = false;
  local tempEvent = uiController:FindEvent("Image_CloseEnter");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseEnter);
  tempEvent = uiController:FindEvent("Image_CloseActivity");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseActivity);
  tempEvent = uiController:FindEvent("Image_Hint");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Hint);
  tempEvent = uiController:FindEvent("Image_Refresh");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Refresh);
end

function UIActivityModel.OnOpen()
  Network.Send(1, 16);

  ActivityModel.CheckHasActiveActivity();

  this.ResetNowActivity();

  if ActivityModel.hasActiveActivity then
    return true;
  else
    ShowCenterMessage(string.Get(20377));
    Function.UpdateVisible();
    return false;
  end
end

function UIActivityModel.OnClose()
  ActivityModel.showObj = EModelObj.Enter;
  ActivityModel.openUI = false;
  this.SetScrollMask(false);
  local hasShow = false;

  for _, v in pairs(nowActivity) do
    if v.showDot then
      hasShow = true;
      break;
    end
  end

  if not hasShow then
    RedDot.Remove(ERedDot.ActivityModelHasAct);
  end

  table.Clear(nowActivity);

  if not ActivityModel.hasActiveActivity then
    Function.UpdateVisible();
  end

  return true;
end

function UIActivityModel.OnVisible(visible)
  if visible then
    this.UpdateUI(true);
  end
end

function UIActivityModel.OnClick_Hint()
  UI.Open(UITutorialHint, 0, this, activityData.title, string.Get(activityData.hintStringId));
end

function UIActivityModel.OnClick_CloseEnter()
  CGTimer.RemoveListener(this.UpdateEnterTime);
  ActivityModel.CheckHasActiveActivity();
  this.SetScrollMask(false);
  UI.Close(UIActivityModel);
end

function UIActivityModel.OnClick_CloseActivity()
  page = 1;
  ActivityModel.showObj = EModelObj.Enter;
  ActivityModel.openUI = false;
  this.SetScrollMask(false);
  this.CloseSpecialActivity();
  CGTimer.RemoveListener(this.UpdateActivityTime);
  this.UpdateUI();
end

function UIActivityModel.OnClick_Refresh()
  if not isSendRefresh then
    if ActivityModel.showObj ~= EModelObj.Activity then return end

    Network.Send(124, 1);
    isSendRefresh = true;
    CGTimer.DoCountdown(this.CheckSendRefresh, 30); --30秒回應一次玩家的點擊
  else
    ShowCenterMessage(string.Get(22841)); --請稍後再點擊
  end
end

function UIActivityModel.SetOnClick_Refresh(func)
  local tempEvent = this.uiController:FindEvent("Image_Refresh");
  tempEvent:ClearListener();
  tempEvent:SetListener(EventTriggerType.PointerClick, func);
end

function UIActivityModel.CheckSendRefresh()
  isSendRefresh = false;
end

function UIActivityModel.UpdateUI(reset)
  gameObject_Enter:SetActive(ActivityModel.showObj == EModelObj.Enter);
  gameObject_Activity:SetActive(ActivityModel.showObj == EModelObj.Activity);

  this.UpdateEnter();
  this.UpdateActivity(reset);

end

function UIActivityModel.UpdateRedDot()
  if not CGTimer.ContainsListener(this.UpdateRedDot) then
    CGTimer.AddListener(this.UpdateRedDot, 180, false);
  end

  Network.Send(124, 4);

  local hasShow = false;

	this.ResetNowActivity();

  for _, v in pairs(nowActivity) do
    if v.showDot then
      hasShow = true;
      break;
    end
  end

  if hasShow then
    RedDot.Add(ERedDot.ActivityModelHasAct);
  else
    RedDot.Remove(ERedDot.ActivityModelHasAct);
  end

  Function.UpdateVisible();
end

function UIActivityModel.UpdateEnter()
  if ActivityModel.showObj ~= EModelObj.Enter then return end

  this.ResetNowActivity();

  text_Name.text = string.Get(24882);
  scrollContent_Festival:Reset(table.Count(nowActivity));

  if not CGTimer.ContainsListener(this.UpdateEnterTime) then
    CGTimer.AddListener(this.UpdateEnterTime, 1, true);
  end
end

function UIActivityModel.UpdateEnterTime()
  for i = 1, table.Count(scrollItems_Festival) do
    if scrollItems_Festival[i-1].getEvent.parameter then
      local status, text = ActivityModel.GetActivityStatus(scrollItems_Festival[i-1].getEvent.parameter);
      scrollItems_Festival[i-1].text_Time.text = text;
      scrollItems_Festival[i-1].text_Award.text = ActivityModel.GetActivityStatusString(status);
      if status == EFestivalStatus.Upcoming then
        scrollItems_Festival[i-1].text_Time.color = Color.Yellow;
      elseif status == EFestivalStatus.Open then
        scrollItems_Festival[i-1].text_Time.color = Color.Cyan;
      end
    end
  end
end

function UIActivityModel.UpdateActivity(reset)
  if ActivityModel.showObj ~= EModelObj.Activity then return end

  activityData = ActivityModel.ActivityData[ActivityModel.openActivityId];

  if activityData == nil then
    logError("!!!activityID "..ActivityModel.openActivityId.." didn't exist!!!");
    ShowCenterMessage("!!!activityID "..ActivityModel.openActivityId.." didn't exist!!!");
    return false;
  end

  pageData = activityData.page;

  if pageData[page] ~= nil then
    missionsData = ActivityModel.SortMission(pageData[page].missions);
  else
    missionsData = ActivityModel.SortMission(activityData.missionsWithNoPage);
  end

  gameObject_IconRoot:SetActive(activityData.coinCount ~= 0);

  for i = 1, 2 do
    showIcons[i].gameObject:SetActive(activityData.coinCount >= i);
  end

  text_Title.text = activityData.title;
  text_Description.text = activityData.description;

  gameObject_PageRoot:SetActive(activityData.pageCount > 1);

  TextureManager.SetPng(ETextureUseType.UI, string.Concat(string.format("ActModelPic_%03d", activityData.rolePicture)), rawImage_Tachie);

  this.UpdatePage(reset);
  this.UpdateCoin();
  this.UpdateExchange(reset);

  if not CGTimer.ContainsListener(this.UpdateActivityTime) then
    CGTimer.AddListener(this.UpdateActivityTime, 1, true);
  end
end

function UIActivityModel.UpdatePage(reset)
  if activityData.pageCount ~= 0 then
    if pageData == nil then
      logError("!!!分頁資料為空!!!");
      ShowCenterMessage("!!!分頁資料為空!!!");
      return;
    end

    if pageData[page] == nil then
      logError("!!!分頁"..page.."資料為空!!!");
      ShowCenterMessage("!!!分頁"..page.."資料為空!!!");
      return;
    end

    missionsData = ActivityModel.SortMission(pageData[page].missions);
    text_Description.text = pageData[page].description;
  end

  if reset then
    scrollContent_Page:Reset(table.Count(pageData));
  else
    scrollContent_Page:Refresh();
  end
end

function UIActivityModel.UpdateExchange(reset)
  if missionsData == nil then
    logError("!!!任務資料為空!!!");
    ShowCenterMessage("!!!任務資料為空!!!");
    return;
  end

  if reset then
    scrollContent_Exchange:Reset(table.Count(missionsData));
  else
    scrollContent_Exchange:Refresh();
  end
end

function UIActivityModel.UpdateCoin()
  if activityData.coin == nil then
    logError("!!!活動積分資料為空!!!")
    ShowCenterMessage("!!!活動積分資料為空!!!");
    return;
  end

  for i = 1, activityData.coinCount do
    if ActivityModel.ActivityCoin[activityData.coin[i].id] == nil then
      logError("!!!活動積分資料"..activityData.coin[i].id.."不存在!!!")
      ShowCenterMessage("!!!活動積分資料"..activityData.coin[i].id.."不存在!!!");
      return;
    end

    local iconItemId = ActivityModel.ActivityCoin[activityData.coin[i].id].itemId;
    itemDatas[CONST_COIN_ITEM_ID + i - 1].name = ActivityModel.ActivityCoin[activityData.coin[i].id].name;
    itemDatas[CONST_COIN_ITEM_ID + i - 1].iconId = itemDatas[iconItemId].iconId;
    itemDatas[CONST_COIN_ITEM_ID + i - 1].price = activityData.coin[i].id; --借用價格欄紀錄coin id;
    showIcons[i].icon:SetItem(CONST_COIN_ITEM_ID + i - 1);
    showIcons[i].text_Quant.text = ActivityModel.ActivityCoin[activityData.coin[i].id].quant;
  end
end

function UIActivityModel.UpdateActivityTime()
  local isAct = false;

  local status, statusText = ActivityModel.GetActivityStatus(activityData.activityId);
  text_Time.text = statusText;
  if status == EFestivalStatus.Upcoming then
    text_Time.color = Color.Yellow;
  elseif status == EFestivalStatus.Open then
    text_Time.color = Color.Cyan;
  end

  if Contains(status, EFestivalStatus.Upcoming, EFestivalStatus.Open, EFestivalStatus.ActivityEnd) then
    isAct = true;
  end

  if not isAct and CGTimer.ContainsListener(this.UpdateActivityTime) then
    CGTimer.RemoveListener(this.UpdateActivityTime);
  end
end

function UIActivityModel.OnInitialize_ScrollContent_Festival(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Festival[itemIndex] = {};
    scrollItems_Festival[itemIndex].text_Title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");
    scrollItems_Festival[itemIndex].text_Description = scrollItems[itemIndex]:Find("Text_Description"):GetComponent("Text");
    scrollItems_Festival[itemIndex].text_Award = scrollItems[itemIndex]:Find("Text_Award"):GetComponent("Text");
    scrollItems_Festival[itemIndex].text_Time = scrollItems[itemIndex]:Find("Text_Time"):GetComponent("Text");
    scrollItems_Festival[itemIndex].getEvent = scrollItems[itemIndex].gameObject:GetComponent("UIEvent");
    scrollItems_Festival[itemIndex].getEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Festival);
    scrollItems_Festival[itemIndex].image_Dot = scrollItems[itemIndex]:Find("Image_Dot");
  end
end

function UIActivityModel.OnItemChange_ScrollContent_Festival(dataIndex, itemIndex)
  local index = dataIndex + 1;

  if nowActivity[index] == nil then
    logError("festivalInfos == nil");
    return false;
  end

  scrollItems_Festival[itemIndex].image_Dot.gameObject:SetActive(nowActivity[index].showDot);
  scrollItems_Festival[itemIndex].text_Title.text = nowActivity[index].title;
  scrollItems_Festival[itemIndex].text_Description.text = nowActivity[index].description;
  TextureManager.SetPng(ETextureUseType.UI, string.Concat( string.format("ActModelBanner_%03d", nowActivity[index].picture)),  scrollItems_Festival[itemIndex].getEvent.gameObject:GetComponent("RawImage"));
  scrollItems_Festival[itemIndex].getEvent.parameter = nowActivity[index].activityId;

  return true;
end

function UIActivityModel.OnInitialize_ScrollContent_Page(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Page[itemIndex] = {};
    scrollItems_Page[itemIndex].BG = scrollItems[itemIndex]:GetComponent("Image");
    scrollItems_Page[itemIndex].Title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");
    scrollItems_Page[itemIndex].RedDot = scrollItems[itemIndex]:Find("Image_RedDot"):GetComponent("Image");
    scrollItems_Page[itemIndex].event = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_Page[itemIndex].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Page);
    scrollItems_Page[itemIndex].event.parameter = itemIndex + 1;
  end
end

function UIActivityModel.OnItemChange_ScrollContent_Page(dataIndex, itemIndex)
  local index = dataIndex + 1;

  if pageData[index] == nil then
    logError("!!!分頁"..index.."無資料!!!")
    ShowCenterMessage("!!!分頁"..index.."無資料!!!")
    return false;
  end

  scrollItems_Page[itemIndex].Title.text = pageData[index].title;
  scrollItems_Page[itemIndex].RedDot.enabled = pageData[index].showDot;
  if pageData[index].title == "" then
    this.HidePage(itemIndex);
	else
	  scrollItems_Page[itemIndex].BG.enabled = true;
  end

  if page == index then
    scrollItems_Page[itemIndex].BG.sprite = UI.GetSprite("bod072");
  else
    scrollItems_Page[itemIndex].BG.sprite = UI.GetSprite("bod071");
  end

  return true;
end

function UIActivityModel.OnInitialize_ScrollContent_Exchange(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Exchange[itemIndex] = {};
    scrollItems_Exchange[itemIndex].itemChip = {};
    scrollItems_Exchange[itemIndex].text_Status = scrollItems[itemIndex]:Find("Text_Status"):GetComponent("Text");
    scrollItems_Exchange[itemIndex].text_Schedule = scrollItems[itemIndex]:Find("Text_Schedule"):GetComponent("Text");
    scrollItems_Exchange[itemIndex].text_ChangeKind = scrollItems[itemIndex]:Find("Text_ChangeKind"):GetComponent("Text");
    scrollItems_Exchange[itemIndex].text_Exchange = scrollItems[itemIndex]:Find("Image_Exchange"):Find("Text_Exchange"):GetComponent("Text");

    for i = 1, 5 do
      scrollItems_Exchange[itemIndex].itemChip[i] = {};
      scrollItems_Exchange[itemIndex].itemChip[i].gameObject = scrollItems[itemIndex]:Find(string.Concat("Image_ItemChip (", i, ")")).gameObject;
      scrollItems_Exchange[itemIndex].itemChip[i].item = UIItemController.New(scrollItems_Exchange[itemIndex].itemChip[i].gameObject.transform);
      scrollItems_Exchange[itemIndex].itemChip[i].text_Quant = scrollItems_Exchange[itemIndex].itemChip[i].gameObject.transform:Find("Text_Quant"):GetComponent("Text");
    end

    scrollItems_Exchange[itemIndex].image_Arrow = scrollItems[itemIndex]:Find("Image_Arrow"):GetComponent("Image");
    scrollItems_Exchange[itemIndex].image_BGMask = scrollItems[itemIndex]:Find("Image_BGMask"):GetComponent("Image");
    scrollItems_Exchange[itemIndex].text_Title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");
    scrollItems_Exchange[itemIndex].awardGameObject = scrollItems[itemIndex]:Find("Image_ItemAward").gameObject;
    scrollItems_Exchange[itemIndex].itemAward = UIItemController.New(scrollItems_Exchange[itemIndex].awardGameObject.transform);
    scrollItems_Exchange[itemIndex].image_Discount = scrollItems_Exchange[itemIndex].awardGameObject.transform:Find("Image_Discount"):GetComponent("Image");
    scrollItems_Exchange[itemIndex].text_Discount = scrollItems_Exchange[itemIndex].awardGameObject.transform:Find("Text_Discount"):GetComponent("Text");
    scrollItems_Exchange[itemIndex].getEvent = scrollItems[itemIndex]:Find("Image_Exchange"):GetComponent("UIEvent");
    scrollItems_Exchange[itemIndex].getEventObject = scrollItems_Exchange[itemIndex].getEvent.gameObject;
    scrollItems_Exchange[itemIndex].getEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Change);
  end
end

function UIActivityModel.OnItemChange_ScrollContent_Exchange(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local mission = missionsData[index];

  if mission == nil then
    logError("!!!任務缺資料!!!")
    ShowCenterMessage("!!!任務缺資料!!!")
    return false;
  end

  local missionString = ActivityModel.GetMissionTitle(mission);

  scrollItems_Exchange[itemIndex].text_Title.text = missionString;
  scrollItems_Exchange[itemIndex].image_Arrow.enabled = mission.type == EMissionType.Exchange;
  scrollItems_Exchange[itemIndex].awardGameObject:SetActive(mission.type == EMissionType.Exchange);

  if mission.type == EMissionType.Exchange then
    if mission.itemChange[1].kind == 2 then
      scrollItems_Exchange[itemIndex].itemAward:SetItem(mission.itemChange[1].itemId, mission.itemChange[1].quant);
    elseif mission.itemChange[1].kind == 1 then
      scrollItems_Exchange[itemIndex].itemAward:SetItem(ActivityModel.ActivityCoin[mission.itemChange[1].itemId].itemId, mission.itemChange[1].quant);
    end
    scrollItems_Exchange[itemIndex].text_Exchange.text = string.Get(20253);
    scrollItems_Exchange[itemIndex].image_Discount.enabled = mission.discount > 100;

    scrollItems_Exchange[itemIndex].text_Discount.text,
    scrollItems_Exchange[itemIndex].image_Discount.sprite = ActivityModel.GetDiscountInfo(mission.discount);

    for i = 1, 5 do
      if i <= 2 then
        local bagQuant = 0;
        local quant = 0;

        if mission.itemCost[i].kind == 1 then
          bagQuant = ActivityModel.ActivityCoin[mission.itemCost[i].itemId].quant;
        else
          bagQuant = Item.GetItemCount(mission.itemCost[i].itemId);
        end

        quant = Item.GetSimpleValue(bagQuant, true);

        if bagQuant >= mission.itemCost[i].quant then
          scrollItems_Exchange[itemIndex].itemChip[i].text_Quant.text = string.GetColorText(
            string.Concat(quant,"/", mission.itemCost[i].quant), Color.Black);
        else
          scrollItems_Exchange[itemIndex].itemChip[i].text_Quant.text = string.GetColorText(
            string.Concat(quant,"/", mission.itemCost[i].quant), Color.Red);
        end

        scrollItems_Exchange[itemIndex].itemChip[i].gameObject:SetActive(mission.itemCost[i].itemId ~= 0);
        scrollItems_Exchange[itemIndex].itemChip[i].item:SetItem(ActivityModel.GetActivityCoinOrItem(mission.itemCost[i].kind, mission.itemCost[i].itemId));
      else
        scrollItems_Exchange[itemIndex].itemChip[i].gameObject:SetActive(false);
      end
    end
  else
    scrollItems_Exchange[itemIndex].text_Exchange.text = string.Get(22739);

    for i = 1, 5 do
      local itemId = ActivityModel.GetActivityCoinOrItem(mission.itemChange[i].kind, mission.itemChange[i].itemId);

      scrollItems_Exchange[itemIndex].itemChip[i].gameObject:SetActive(itemId ~= 0);
      scrollItems_Exchange[itemIndex].itemChip[i].item:SetItem(itemId, mission.itemChange[i].quant);
      scrollItems_Exchange[itemIndex].itemChip[i].text_Quant.text = "";
    end
  end


  if mission.getLimit > 0 then
    scrollItems_Exchange[itemIndex].text_Schedule.text = string.format(
      string.Get(21509),
      ActivityModel.PlayerAwardCount[mission.id].get,
      mission.getLimit);
  else
    scrollItems_Exchange[itemIndex].text_Schedule.text = "";
  end

  scrollItems_Exchange[itemIndex].text_ChangeKind.text = ActivityModel.GetCompleteCount(mission);
  scrollItems_Exchange[itemIndex].image_BGMask.enabled = mission.status == ESortOrder.Complete;
  scrollItems_Exchange[itemIndex].getEventObject:SetActive(mission.status == ESortOrder.Available);
  scrollItems_Exchange[itemIndex].text_Status.text = ActivityModel.GetStatusString(mission);
  scrollItems_Exchange[itemIndex].getEvent.parameter = index;

  return true;
end

function UIActivityModel.OnClick_Festival(uiEvent)
  local activityId = uiEvent.parameter;

  local temp = ActivityModel.ActivityData[activityId];

  if temp == nil then
    logError("!!!activityID "..activityId.." didn't exist!!!");
    ShowCenterMessage("!!!activityID "..activityId.." didn't exist!!!");
    return;
  end

  if DateTime.Compare(CGTimer.serverTime, temp.startTime) < 0 then
    ShowCenterMessage(string.Get(22004));
    return;
  end

  if temp.isOpen == 0 or DateTime.Compare(CGTimer.serverTime, temp.endShowTime) > 0 then
    ShowCenterMessage(string.Get(20377));
    return;
  end

  ActivityModel.openUI = true;
  ActivityModel.openActivityId = activityId;
  Network.Send(124, 1);
end

function UIActivityModel.OnClick_Change(uiEvent)
  local mission = missionsData[uiEvent.parameter];

  if mission.type == EMissionType.Exchange then
    local count = 99;
    for i = 1, 2 do
      local max = 1;

      if mission.itemCost[i].itemId == 0 then break end
      if mission.itemCost[i].kind == 1 then
        max = ActivityModel.ActivityCoin[mission.itemCost[i].itemId].quant;
      elseif mission.itemCost[i].kind == 2 then
        max = Item.GetItemCount(mission.itemCost[i].itemId);
      end

      local temp = math.floor(max / mission.itemCost[i].quant);

      if temp < count then
        count = temp;
      end
    end

    if mission.getLimit ~= 0 then
      count = math.min(count, mission.getLimit);
    end

    ShowCountInput(string.Get(20253), 1, count,
      function(text)
        if text == nil then return end
        ActivityModel.SendGetAward(mission.id, tonumber(text));
      end
    );
  else
    ActivityModel.SendGetAward(mission.id, 1);
  end
end

function UIActivityModel.OnClick_Page(uiEvent)
  local index = uiEvent.parameter;

  if page ~= index then
    page = index;
    ActivityModel.CheckOnClickSpecialACtivity(page);
    this.UpdateActivity(true);
  end
end


function UIActivityModel.GetUIActivityModelRoot(specialActivity)
  if specialActivity == ESpecialActivity.Bingo then
    return canvasGroup_Image_ScrollRectBG.gameObject, gameObject_UIBingo;  --送出BingoUI的Prefab跟ActivityModelUI的rootObject
  elseif specialActivity == ESpecialActivity.Monopoly then
    return canvasGroup_Image_ScrollRectBG.gameObject, gameObject_Monoply; --送出Monoply的Prefab跟ActivityModelUI的rootObject
  end
end

function UIActivityModel.HidePage(page)
  scrollItems_Page[page].BG.enabled = false;
  scrollItems_Page[page].RedDot.enabled = false;
end

function UIActivityModel.SetScrollMask(active)
  image_ScrollMask.enabled = active;
end

function UIActivityModel.CloseSpecialActivity()
  UIBingo.OnClose();
	UIActivityMonopoly.OnClose();
  UI.Close(UILeaderboardSimple);
end

function UIActivityModel.ResetNowActivity()
  nowActivity = {};
  local _ = false;
  _, nowActivity = ActivityModel.CheckHasActiveActivity();
  for _, v in pairs(nowActivity) do
    if v.isCurrentActivity then
      ActivityModel.GetPageAndActivityRedDot(v.activityId);
    end
  end
end