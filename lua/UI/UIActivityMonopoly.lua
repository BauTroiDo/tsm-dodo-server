UIActivityMonopoly = {}
local this = UIActivityMonopoly;
this.name = "UIActivityMonopoly";
this.uiController = nil;
this.waitCallBack = false;
this.openUI = false;
this.EActivityMonopoly = {
  singleBar = 5,
  multipleBar = 17
}

local progressBarType = {
  Once = 1,
  Multiple = 2,
}

local activityKind = {
	singleBar = 5,
	multipleBar = 17,
}

local CONST_BLISSBAG_ITEM_ID = 45000;

local indicator;
local gameObject_UIActivityMonopoly;
local gameObject_BoardArea;
local boardAreaRectTransform;
local gameObject_IndicatorLeftSteps

local gameObject_ProgressBarOne;
local gameObject_ProgressBarTwo;
local gameObject_ProgressBarOne_ItemRoot;
local gameObject_ProgressBarTwo_ItemRoot = {};

local image_BarOne_Border;
local image_BarOne_White;
local image_BarTwo_Items = {};
local image_BarTwo_Hints = {};

local image_RollDiceButton;
local image_AutoRollDiceSwitch;
local image_MonopolyCell;
local image_MonopolyBoardBG;

local rawImage_ProgressBarBGPic;
local rawImage_Title;
local rawImage_RollingDice;
local rawImage_StillDice;
local uiRawImage_RollingDice;
local uiRawImage_StillDice;

local text_IndicatorLeftSteps;
local text_ProgressCount;
local text_CurrentCount;
local text_AwardCount;
local text_RollDice;
local text_AutoRoll;
local text_Progresspercent = {};

local slider_ProgressBarOne;
local slider_ProgressBarTwo;

local event_RollDiceButton;

local rollButtonActiveName = "but001D";
local rollButtonGrayName = "but001L";
local diceResultImageName = "SceneEffect_0003";
local diceResultImagePage = 6;

local diceRollingEffectName = "SceneEffect_0002";
local diceRollingEffectPage = 8;
local diceRollingEffectDefaultRect = Rect.New(0, 1 / diceRollingEffectPage, 1, 1 / diceRollingEffectPage);
local diceEffectInterval = 0.08;
local diceUVRect = {};

local indicatorLeftStepsTextOffset = Vector3.New(0, 60, 0);

local progressBarKind;
local currentCharge; --當前擲骰次數
local totalCharge;   --進度條滿需要的擲骰次數

local isInitialize = false;
local clickResult = false;
local lastClickProgessPrizeTime = nil;
local lastClickRollTime = nil;
local rewardMissionId;
local progressRewardMissionId;
local progressRewardMissionGet;
local sizeOfBoard = 4; --可改成讀表
local autoRoll;
local cellCount;
local currentSteps; --當前的總步數(由S傳下)
local currentCellIndex; --當前的格子編號
local currentRound; --當前的總圈數
local diceSize = 90;
local animationSteps = 8; --可以控制表演速度
local diceSteps;
local progressMissionAwardCount = 3; --進度條多獎勵的獎勵數量
local isMoving = false;
local Cells = {};
local originBlissBagData = {};
local originItemData = {};
local originTexture = {};
local sortedActivtiyMdoelMission = {};
local onceBarMission = {};
local multiBarMission = {};

--region Initialize
function UIActivityMonopoly.Initialize(go)
	this.uiController = go:GetComponent("UIController");
	this.uiController.onOpen = this.OnOpen;
	this.uiController.onClose = this.OnClose;
	this.uiController.onVisible = this.OnVisible;
	gameObject_BoardArea = this.uiController:FindGameObject("GameObject_BoardArea");
	image_MonopolyCell = this.uiController:FindImage("Image_MonopolyCell");
	image_MonopolyBoardBG = this.uiController:FindImage("Image_MonopolyBoardBG");
	image_RollDiceButton = this.uiController:FindImage("Image_RollDice");
	image_AutoRollDiceSwitch = this.uiController:FindImage("Image_Auto");
	image_AutoRollDiceSwitch.sprite = UI.GetSwitchSprite(false);

	rawImage_Title = this.uiController:FindRawImage("RawImage_MonopolyTitle");
	rawImage_RollingDice = this.uiController:FindRawImage("RawImage_RollingDice");
	uiRawImage_RollingDice = rawImage_RollingDice.gameObject:GetComponent("UIRawImage");
  rawImage_RollingDice.enabled = false;
  rawImage_RollingDice.rectTransform.sizeDelta = Vector2.New(diceSize, diceSize);

  rawImage_StillDice = this.uiController:FindRawImage("RawImage_StillDice");
	uiRawImage_StillDice = rawImage_StillDice.gameObject:GetComponent("UIRawImage");
  rawImage_StillDice.enabled = false;
  rawImage_StillDice.rectTransform.sizeDelta = Vector2.New(diceSize, diceSize);

	text_IndicatorLeftSteps = this.uiController:FindText("Text_IndicatorLeftSteps");
	event_RollDiceButton = this.uiController:FindEvent("Image_RollDice")
	event_RollDiceButton:SetListener(EventTriggerType.PointerClick, this.OnClick_RollDice);

	gameObject_IndicatorLeftSteps = text_IndicatorLeftSteps.gameObject;
	boardAreaRectTransform = gameObject_BoardArea:GetComponent("RectTransform");

	for index = 1, diceResultImagePage do
    diceUVRect[index] = Rect.New(0, (diceResultImagePage - index) / diceResultImagePage, 1, 1 / diceResultImagePage);
  end


	gameObject_ProgressBarOne = this.uiController:FindGameObject("ProgressBarOne");
  slider_ProgressBarOne = this.uiController:FindSlider("Slider_ProgressBarOne");
  gameObject_ProgressBarTwo = this.uiController:FindGameObject("ProgressBarTwo");
  slider_ProgressBarTwo = this.uiController:FindSlider("Slider_ProgressBarTwo");
  image_BarOne_Border = this.uiController:FindImage("Image_AwardBar_Border");
  image_BarOne_White = this.uiController:FindImage("Image_AwardBar_White");
  image_BarOne_Border.gameObject:SetActive(false);
  image_BarOne_White.gameObject:SetActive(false);
  this.SetProgressBar()
  gameObject_ProgressBarOne:SetActive(progressBarKind == progressBarType.Once);
  gameObject_ProgressBarTwo:SetActive(progressBarKind == progressBarType.Multiple);

  for i = 1, 3 do
    image_BarTwo_Items[i] = {};
  end

  for i = 1, 3 do
    text_Progresspercent[i] = this.uiController:FindText(string.Concat("Text_percentage", i));
    image_BarTwo_Items[i].image = this.uiController:FindImage(string.Concat("Image_Award_", i));
    image_BarTwo_Items[i].border = this.uiController:FindImage(string.Concat("Image_Border", i));
    image_BarTwo_Items[i].white = this.uiController:FindImage(string.Concat("Image_White", i));
    image_BarTwo_Items[i].lock = this.uiController:FindImage(string.Concat("Image_Lock", i));
    image_BarTwo_Items[i].border.gameObject:SetActive(false);
    image_BarTwo_Items[i].white.gameObject:SetActive(false);
    image_BarTwo_Items[i].event = this.uiController:FindEvent(string.Concat("Image_Award_", i))
    image_BarTwo_Items[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_MultiProgressPrize);
    image_BarTwo_Items[i].event.parameter = i;
    table.insert(image_BarTwo_Hints, this.uiController:FindImage(string.Concat("Image_percentage", i)));
  end

  rawImage_ProgressBarBGPic = this.uiController:FindRawImage("RawImage_ProgressBarBGPic");
  text_ProgressCount = this.uiController:FindText("Text_ProgressCount");
  text_CurrentCount = this.uiController:FindText("Text_CurrentCount");
  text_RollDice = this.uiController:FindText("Text_RollDice");
  text_AwardCount = this.uiController:FindText("Text_AwardCount");
  text_AutoRoll = this.uiController:FindText("Text_AutoRoll");
  text_RollDice.text = string.Get(24947); -- GO

  local tempEvent = this.uiController:FindEvent("Image_Auto");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AutoSwitch);
  tempEvent = this.uiController:FindEvent("Image_RollDice");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_RollDice);
  tempEvent = this.uiController:FindEvent("Image_Award")
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_OnceProgressPrize);
end
--endregion


--region OnAction
function UIActivityMonopoly.OnOpen()
  if gameObject_UIActivityMonopoly ~= nil then
    gameObject_UIActivityMonopoly:SetActive(true);
  end
  this.UpdateUI();
  CGTimer.AddListener(this.Update, 0.05, true);
  text_IndicatorLeftSteps.text = "";
  this.openUI = true;
  this.GetCurrentSteps();
  this.UpdateBoardData();
  return true
end

function UIActivityMonopoly.OnClose()
	this.openUI = false;
  autoRoll = false;
	if indicator ~= nil then
    this.EndMoveIndicator(indicator);
	end

	CGTimer.RemoveListener(this.Update);
  this.ResetBorrowData();
  if gameObject_UIActivityMonopoly ~= nil then
    gameObject_UIActivityMonopoly:SetActive(false);
    this.Clear();
  end
end

function UIActivityMonopoly.OnClick_RollDice()
	if isMoving == true then
    ShowCenterMessage(string.Get(23423)); --骰動中請稍候
    return
  end
	isMoving = true;

  clickResult, lastClickRollTime = UIBingo.CheckClickTime(lastClickRollTime);
  if not clickResult then isMoving = false; return end;

  if ActivityModel.GetActivityStatus(ActivityModel.openActivityId) == EFestivalStatus.ActivityEnd then
    ShowCenterMessage(string.Get(20377)); --活動結束
    this.UpdateUI();
		isMoving = false;
    return
  end;

  local bagItems = Item.GetBagCount(EThings.Bag, 0);
  local bagEmptyMax = Item.currentThingGrid;
  if bagItems > bagEmptyMax - 2 then
    ShowCenterMessage(string.Get(23424)); --請清理出兩格的背包空間後再來遊玩吞食大富翁，獎品好禮等著您唷！
		isMoving = false;
    return
  end

  sendBuffer:Clear();
  sendBuffer:WriteByte(ActivityModel.openActivityId);
  sendBuffer:WriteByte(ESpecialActivity.Monopoly);
	sendBuffer:WriteInt32(1); --擲骰子
	Network.Send(124, 5, sendBuffer);
end

function UIActivityMonopoly.OnClick_AutoSwitch(uiEvent)
  this.SetAutoRoll();
end

function UIActivityMonopoly.OnClick_MultiProgressPrize(event)
  if isMoving == true then
    ShowCenterMessage(string.Get(23423)); --骰動中請稍候
    return
  end

  clickResult, lastClickProgessPrizeTime = UIBingo.CheckClickTime(lastClickProgessPrizeTime);
  if not clickResult then return end;

	progressRewardMissionId = ActivityModel.openActivityId * 1000 + 300 + event.parameter;
	progressRewardMissionGet = ActivityModel.PlayerAwardCount[progressRewardMissionId].get;
  sendBuffer:Clear();
  sendBuffer:WriteUInt32(progressRewardMissionId);
  sendBuffer:WriteUInt32(1);
  Network.Send(124, 3 , sendBuffer);
end

function UIActivityMonopoly.OnClick_OnceProgressPrize()
  if isMoving == true then
    ShowCenterMessage(string.Get(23423)); --骰動中請稍候
    return
  end

	clickResult, lastClickProgessPrizeTime = UIBingo.CheckClickTime(lastClickProgessPrizeTime);
	if not clickResult then return end;
	local result = math.floor(currentCharge / totalCharge);
  if result == 0 then return end;

	progressRewardMissionId = ActivityModel.openActivityId * 1000 + 300 + 1;
	progressRewardMissionGet = ActivityModel.PlayerAwardCount[progressRewardMissionId].get;
	sendBuffer:Clear();
	sendBuffer:WriteUInt32(progressRewardMissionId);
	sendBuffer:WriteUInt32(this.GetAvailableRewardCount());
	Network.Send(124, 3, sendBuffer);
end

function UIActivityMonopoly.ResetBorrowData()
  for k, v in pairs(originItemData) do
    itemDatas[k].name = v.name;
    itemDatas[k].description = v.description;
    itemDatas[k].specialAbility = v.specialAbility;
    itemDatas[k].btnState = v.btnState;
    itemDatas[k].iconId = v.iconId;
    itemDatas[k].openUsed = v.openUsed;
    itemDatas[k].element = v.element;
    itemDatas[k].kind = v.kind;
	end

  for k, v in pairs(originBlissBagData) do
    blissBagDatas[k] = v;
  end

  for k, v in pairs(originTexture)do
    Cells[k].item.rawImage_Icon.texture = v.texture;
    Cells[k].item.rawImage_Icon.color = v.color;
  end
end
--endregion

--region update
function UIActivityMonopoly.Update()
	this.UpdateIndicator(indicator);
	if ActivityModel.ActivityData[ActivityModel.openActivityId].kind == activityKind.singleBar then
		this.UpdateProgressBar(progressBarType.Once);
	elseif ActivityModel.ActivityData[ActivityModel.openActivityId].kind == activityKind.multipleBar then
		this.UpdateProgressBar(progressBarType.Multiple);
	end
end

function UIActivityMonopoly.UpdateUI()
	image_AutoRollDiceSwitch.sprite = UI.GetSwitchSprite(autoRoll);
	TextureManager.SetPng(ETextureUseType.None, diceRollingEffectName, rawImage_RollingDice);
  TextureManager.SetPng(ETextureUseType.None, diceResultImageName, rawImage_StillDice);
  if ActivityModel.GetActivityStatus(ActivityModel.openActivityId) == EFestivalStatus.ActivityEnd then
    image_RollDiceButton.sprite = UI.GetSprite(rollButtonGrayName);
  else
    image_RollDiceButton.sprite = UI.GetSprite(rollButtonActiveName);
  end

  this.SetProgressBar();
  this.SetProgressReward();

  if ActivityModel.ActivityData[ActivityModel.openActivityId].kind == activityKind.singleBar then
    gameObject_ProgressBarOne:SetActive(true);
    gameObject_ProgressBarTwo:SetActive(false);
  elseif ActivityModel.ActivityData[ActivityModel.openActivityId].kind == activityKind.multipleBar then
    gameObject_ProgressBarOne:SetActive(false);
    gameObject_ProgressBarTwo:SetActive(true);
  end
end

function UIActivityMonopoly.UpdateIndicator(indicator)
	if indicator == nil then return end;
	if indicator.target == nil then this.CheckIndicator() return end;
	if indicator.step == nil then return end;

	local targetPosition = indicator.target.go.transform.position;
	local moveDir = targetPosition - indicator.transform.position;
	local distance = Vector3.Distance(targetPosition, indicator.transform.position);
	if distance <= indicator.currentCell.Boundary then
		this.IndicatorCrossCell(indicator, indicator.target);
	end

	indicator.step = indicator.step + 1;
	if indicator.step >= animationSteps then
		this.IndicatorReachTarget(indicator, targetPosition);
	else
		this.MoveIndicator(indicator, moveDir, indicator.target);
	end
end

function UIActivityMonopoly.UpdateLeftStep()
  text_IndicatorLeftSteps.text = text_IndicatorLeftSteps.text - 1;
end

function UIActivityMonopoly.UpdateBoardData()
	if this.openUI == false then return end
	this.RefreshAllCells();
	this.TeleportIndicator(this.GetCurrentCellIndex(currentSteps));
end

function UIActivityMonopoly.UpdateProgressBar(barType)
	local missionActivityId = 0;
	for _, v in pairs(ActivityModel.PlayerAwardCount) do
    if v.mission ~= nil then
      missionActivityId = 0;
      local number = math.floor(v.mission.id / 100);
      local page = math.fmod(number, 10);
      if page == 3 then
        missionActivityId = math.floor(v.mission.id / 1000);
      end
    end

		if barType == progressBarType.Once and v.mission ~= nil then
			if table.Count(onceBarMission) == 0 then
        logError("活動模版進度條任務填表為空表！")
        return
      end;

			if v.mission.conditionId <= EMissionConditionType.MonopolyReward and v.mission.conditionId >= EMissionConditionType.MonopolyDiceThrowTimes and missionActivityId == ActivityModel.openActivityId then
        currentCharge = this.GetConditionComplete();
        text_CurrentCount.text = currentCharge;
        text_AwardCount.text = string.Concat("x", tostring(math.floor(this.GetConditionComplete() / v.mission.conditionCount) - v.get));
				if v.get < v.mission.getLimit and v.mission.getLimit ~= 0 then
					image_BarOne_Border.gameObject:SetActive(true);
					image_BarOne_White.gameObject:SetActive(false);
				elseif v.get >= v.mission.getLimit and v.mission.getLimit ~= 0 then
						image_BarOne_Border.gameObject:SetActive(false);
						image_BarOne_White.gameObject:SetActive(true);
				elseif v.mission.getLimit == 0 then
          if (math.floor(this.GetConditionComplete() / v.mission.conditionCount) - v.get) > 0 then
							image_BarOne_Border.gameObject:SetActive(true);
							image_BarOne_White.gameObject:SetActive(false);
							text_AwardCount.text = string.Concat("x", tostring(math.floor(this.GetConditionComplete() / v.mission.conditionCount) - v.get));
          else
            image_BarOne_Border.gameObject:SetActive(false);
            image_BarOne_White.gameObject:SetActive(true);
            text_AwardCount.text = "";
					end
				end

        totalCharge = v.mission.conditionCount;
        slider_ProgressBarOne.maxValue = totalCharge;
        text_ProgressCount.text = totalCharge;
			end

		elseif barType == progressBarType.Multiple and v.mission ~= nil  then
			if table.Count(multiBarMission) == 0 then
        logError("活動模版進度條任務填表為空表！")
        return
      end;

			if v.mission.conditionId <= EMissionConditionType.MonopolyReward and v.mission.conditionId >= EMissionConditionType.MonopolyDiceThrowTimes and missionActivityId == ActivityModel.openActivityId then
        currentCharge = this.GetConditionComplete();
        text_CurrentCount.text = currentCharge;

        if v.get < v.mission.getLimit and v.mission.getLimit ~= 0 and currentCharge < v.mission.conditionCount then
          image_BarTwo_Items[v.mission.order].lock.gameObject:SetActive(true);
          image_BarTwo_Items[v.mission.order].lock.raycastTarget = false;
          image_BarTwo_Items[v.mission.order].border.gameObject:SetActive(false);
          image_BarTwo_Items[v.mission.order].white.gameObject:SetActive(false);
				elseif v.get < v.mission.getLimit and v.mission.getLimit ~= 0 and currentCharge >= v.mission.conditionCount then
          image_BarTwo_Items[v.mission.order].lock.gameObject:SetActive(false);
          image_BarTwo_Items[v.mission.order].border.gameObject:SetActive(true);
          image_BarTwo_Items[v.mission.order].white.gameObject:SetActive(false);
        elseif v.get >= v.mission.getLimit and v.mission.getLimit ~= 0 then
          image_BarTwo_Items[v.mission.order].lock.gameObject:SetActive(false);
          image_BarTwo_Items[v.mission.order].border.gameObject:SetActive(false);
          image_BarTwo_Items[v.mission.order].white.gameObject:SetActive(true);
        end

        if totalCharge < v.mission.conditionCount then
          totalCharge = v.mission.conditionCount;
        end
        slider_ProgressBarTwo.maxValue = totalCharge;
        text_ProgressCount.text = totalCharge;
      end
    end

    if currentCharge ~= nil then
      local progress;
      progress = math.fmod(currentCharge, totalCharge);
      slider_ProgressBarOne.value = progress;

      if currentCharge < totalCharge then
        slider_ProgressBarTwo.value = currentCharge;
      else
        slider_ProgressBarTwo.value = slider_ProgressBarTwo.maxValue;
      end
    end
	end
end
--endregion

--region Instantiate
function UIActivityMonopoly.SetMonopoly(page)
  if not isInitialize then
    isInitialize = true;
    local rootObj, selfPrefab= UIActivityModel.GetUIActivityModelRoot(ESpecialActivity.Monopoly);
    local go = rootObj.Instantiate(selfPrefab, rootObj.transform);
    go.name = "UI_Monopoly";
    gameObject_UIActivityMonopoly = go;
    this.Initialize(go);

    --sizeOfBoard = 4; --可改成讀表
    text_AutoRoll.text = string.Get(24062); --自動連骰
    if table.Count(Cells) == 0 then
      this.InstantiateCells(sizeOfBoard);
      cellCount = table.Count(Cells);
    end

    local oriRole = Role.player;
    if indicator == nil then
      indicator = UIActivityMonopoly.CreateIndicator(oriRole);
    else
      indicator:UpdateViewJMP(oriRole);
    end

    this.InstantiateProgressBar();
  end

  if page == 3 and ActivityModel.CheckCurrentSpecialActivity(ESpecialActivity.Monopoly) then
    this.OnOpen();
  else
    this.OnClose();
  end
end

function UIActivityMonopoly.CreateIndicator(role)
	local newIndicator = RoleController.Copy(role);
  newIndicator.npcId = 22003;
  newIndicator.titleId = 0;
  newIndicator:UpdateAllView();
	newIndicator:UpdateViewVisible();
	newIndicator.gameObject.layer = LayerMask.NameToLayer("UI");
	newIndicator.transform:SetParent(gameObject_BoardArea.transform);
	newIndicator.transform.localScale = Vector3.one;
	newIndicator.transform.localPosition = Vector3.zero;
	newIndicator.canvas.sortingOrder = this.uiController.canvas.sortingOrder + 1;
	newIndicator.rawImage_Body.enabled = true;
  newIndicator.bodyObject.transform.localPosition = Vector3.New(0, -20, 0);
	newIndicator.tokenRect = newIndicator.rawImage_Body:GetComponent("RectTransform");
	local rayCast = newIndicator.gameObject:GetComponent("GraphicRaycaster");
	newIndicator.hudObject:SetActive(false);
	rayCast.enabled = false;
	gameObject_IndicatorLeftSteps.transform:SetParent(newIndicator.transform);
	gameObject_IndicatorLeftSteps.transform.localPosition = indicatorLeftStepsTextOffset;
	gameObject_IndicatorLeftSteps.transform.localScale = Vector3.one;
	newIndicator.text_Name.enabled = false;
	text_IndicatorLeftSteps.text = "";
	newIndicator.movePath = Queue.New();
	return newIndicator;
end

function UIActivityMonopoly.InstantiateCells(cellBoardLength)
	local areaWidth = boardAreaRectTransform.rect.width;
	local areaHeight = boardAreaRectTransform.rect.height;
	local cellWidth = areaWidth / cellBoardLength;
	local cellHeight = areaHeight / cellBoardLength;
	local initX = -areaWidth / 2 + cellWidth / 2;
	local initY = areaHeight / 2 - cellHeight / 2;

	for i = 1, cellBoardLength do
		for j = 1, cellBoardLength do
			if (i == 1 or i == cellBoardLength or j == 1 or j == cellBoardLength) then
				local x = initX + (i - 1) * cellWidth;
				local y = initY - (j - 1) * cellHeight;

				local position = Vector3.New(x, y, 0);
				local cell = {};
				cell.go = newObject(image_MonopolyCell.gameObject);
				cell.go.transform:SetParent(gameObject_BoardArea.transform);
        cell.cellImage = cell.go:GetComponent("Image");
				local cellRect = cell.go:GetComponent("RectTransform");
				cellRect.localScale = Vector3.one;
				cellRect.sizeDelta = Vector2(75, 75);
				cellRect.localPosition = position;
				cell.go.name = string.Concat("Cell_(", j, ",", i, ")");
				cell.item = UIItemController.New(cell.go.transform:Find("ItemRoot").transform);
				cell.colum = i;
				cell.raw = j;
				table.insert(Cells, cell);
			end
		end
	end

	for _ , v in ipairs(Cells) do --從右下開始(index = 1)順時針繞一圈的index分布
		if v.raw == cellBoardLength then
			v.index = cellBoardLength - v.colum + 1;
			v.defaultDirection = Vector2.New(-1, 0);
		end
		if v.colum == 1 and v.raw ~= cellBoardLength then
			v.index = cellBoardLength + (cellBoardLength - v.raw);
			v.defaultDirection = Vector2.New(0, 1);
		end
		if v.raw == 1 and v.colum ~= 1 then
			v.index = cellBoardLength * 2 - 1 + (v.colum - 1);
			v.defaultDirection = Vector2.New(1, 0);
		end
		if v.colum == cellBoardLength and v.raw ~= cellBoardLength and v.raw ~= 1 then
			v.index = cellBoardLength * 3 - 2 + (v.raw - 1);
			v.defaultDirection = Vector2.New(0, -1);
		end
	end

	table.sort(Cells,function(a,b) return a.index < b.index end);
	for i = 1, table.Count(Cells) do
		if i < table.Count(Cells) then
			this.SetNextCell(Cells[i].index, Cells[i + 1].index);
		else
			this.SetNextCell(Cells[i].index, Cells[1].index);
		end

    if i == 1 then
      Cells[i].cellImage.sprite = UI.GetSprite("com150");
    elseif i == 1 + (cellBoardLength - 1) then
      Cells[i].cellImage.sprite = UI.GetSprite("com147");
    elseif i == 1 + (cellBoardLength - 1) * 2 then
      Cells[i].cellImage.sprite = UI.GetSprite("com149");
    elseif i == 1 + (cellBoardLength - 1) * 3 then
      Cells[i].cellImage.sprite = UI.GetSprite("com148");
    else
      Cells[i].cellImage.sprite = UI.GetSprite("com146");
    end
	end
	image_MonopolyCell.gameObject:SetActive(false);
end

function UIActivityMonopoly.InstantiateProgressBar()
	local index = 1;
  local chargeHint = {};
  local singlebarInit = false;
  local multibarInit = false;
  totalCharge = 0;
	for _, v in pairs(ActivityModel.ActivityData) do
    if v.kind == activityKind.singleBar and not singlebarInit then
      for _, mission in pairs(v.page[3].missions) do
        if mission.conditionId <= EMissionConditionType.MonopolyReward and mission.conditionId >= EMissionConditionType.MonopolyDiceThrowTimes then
          gameObject_ProgressBarOne_ItemRoot = UIItemController.New(this.uiController:FindImage("Image_Award").transform:Find("ItemRoot").transform);
          if mission.itemChange[1].kind == 2 then
            gameObject_ProgressBarOne_ItemRoot:SetItem(mission.itemChange[1].itemId, mission.itemChange[1].quant);
          else
            gameObject_ProgressBarOne_ItemRoot:SetItem(ActivityModel.ActivityCoin[mission.itemChange[1].itemId].itemId, mission.itemChange[1].quant);
          end
          totalCharge = mission.conditionCount;
          slider_ProgressBarOne.maxValue = totalCharge;
          text_ProgressCount.text = totalCharge;
          local activity = {}
          activity.openActivityId = ActivityModel.openActivityId;
          table.insert(onceBarMission, activity);
        end
      end
      singlebarInit = true;
    end

    if v.kind == activityKind.multipleBar and not multibarInit then
      for _, mission in pairs(v.page[3].missions) do
        if mission.conditionId <= EMissionConditionType.MonopolyReward and mission.conditionId >= EMissionConditionType.MonopolyDiceThrowTimes then
          table.insert(gameObject_ProgressBarTwo_ItemRoot, UIItemController.New(this.uiController:FindImage(string.Concat("Image_Award_", index)).transform:Find("ItemRoot").transform));
          if mission.itemChange[1].kind == 2 then
            gameObject_ProgressBarTwo_ItemRoot[index]:SetItem(mission.itemChange[1].itemId, mission.itemChange[1].quant);
          else
            gameObject_ProgressBarTwo_ItemRoot[index]:SetItem(ActivityModel.ActivityCoin[mission.itemChange[1].itemId].itemId, mission.itemChange[1].quant);
          end

          if totalCharge < mission.conditionCount then
            totalCharge = mission.conditionCount;
          end

          slider_ProgressBarTwo.maxValue = totalCharge;
          text_ProgressCount.text = totalCharge;
          text_Progresspercent[index].text = mission.conditionCount;
          table.insert(chargeHint, mission.conditionCount);
          local activity = {}
          activity.openActivityId = ActivityModel.openActivityId;
          table.insert(multiBarMission, activity);
          index = index + 1;
        end
      end

      if table.Count(chargeHint) >= progressMissionAwardCount then
        table.sort(chargeHint);
        local height = slider_ProgressBarTwo.fillRect.rect.height;
        local piece = height / chargeHint[3];
        for i = 1, progressMissionAwardCount do
          image_BarTwo_Hints[i].rectTransform.anchoredPosition = Vector2.New(-44.6, -(height / 2) + piece * chargeHint[i]); --44.6為prefab上取來的X軸數字
        end
      end

      multibarInit = true;
    end
	end
end

function UIActivityMonopoly.SetProgressBar()
  local kind = ActivityModel.ActivityData[ActivityModel.openActivityId].kind;
  if kind == activityKind.singleBar then
    progressBarKind = progressBarType.Once;
  elseif kind == activityKind.multipleBar then
    progressBarKind = progressBarType.Multiple;
  end
end
--endregion

--region SetData
function UIActivityMonopoly.SetAutoRoll(active)
	if active == nil then
		autoRoll = not autoRoll;
	else
		autoRoll = active;
	end

	if isMoving and autoRoll then
		autoRoll = false;
	end

	if image_AutoRollDiceSwitch ~= nil then
		image_AutoRollDiceSwitch.sprite = UI.GetSwitchSprite(autoRoll);
	end
end

---for金手指
function UIActivityMonopoly.SetStep(step)
if type(step)~="number" or (math.floor(step) ~= step) or step <= 1 or step == animationSteps then return end
	animationSteps = step;
end

function UIActivityMonopoly.SetLeftStepText(steps)
	if steps == nil then return end
	text_IndicatorLeftSteps.text = steps;
end

function UIActivityMonopoly.SetNextCell(cellID, nextCellID)
  local cell = this.GetCellData(cellID);
  cell.nextCell = this.GetCellData(nextCellID);
  this.GetCellData(nextCellID).prevCell = cell;
  local direction = cell.nextCell.go.transform.position - cell.go.transform.position;
  cell.Boundary = Vector3.Distance(cell.nextCell.go.transform.position, cell.go.transform.position)/2;
  this.SetCellDefaultDirection(cellID, direction);
end

function UIActivityMonopoly.SetCellDefaultDirection(cellID, direction)
  local defaultDir = direction;
  defaultDir = this.GetMapLocalPosition(defaultDir);
  this.GetCellData(cellID).defaultDirection = defaultDir;
end

function UIActivityMonopoly.GetMapLocalPosition(position)
  position.y = - position.y;
  return position
end

function UIActivityMonopoly.SetIndicatorPath(path, clearPath)
  if indicator == nil or path == nil then
    return
  end
  if clearPath == true then
    indicator.movePath = path;
    indicator.Target = nil;
  else
    local pathCount = path.Count;
    if indicator.movePath == nil then
      indicator.movePath = path;
    else
      for _ = 1, pathCount do
        indicator.movePath:Enqueue(path:Dequeue());
      end
    end
  end
end

function UIActivityMonopoly.SetIndicatorMoveFlag(bool)
	isMoving = bool;
end

function UIActivityMonopoly.SetProgressReward()
  if progressBarKind == progressBarType.Once then
    local mission = ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions[1].itemChange[1];
    if mission.kind == 2 then
      gameObject_ProgressBarOne_ItemRoot:SetItem(mission.itemId, mission.quant);
    else
      gameObject_ProgressBarOne_ItemRoot:SetItem(ActivityModel.ActivityCoin[mission.itemId].itemId, mission.quant);
    end
  elseif progressBarKind == progressBarType.Multiple then
    local missions = {};
    for _, v in pairs(ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions) do
      table.insert(missions, v);
    end

    table.sort(missions, function(a, b) return a.conditionCount < b.conditionCount end)

    for i = 1, table.Count(gameObject_ProgressBarTwo_ItemRoot) do
      if missions[i].itemChange[1].kind == 2 then
        gameObject_ProgressBarTwo_ItemRoot[i]:SetItem(missions[i].itemChange[1].itemId, missions[i].itemChange[1].quant);
      else
        gameObject_ProgressBarTwo_ItemRoot[i]:SetItem(ActivityModel.ActivityCoin[missions[i].itemChange[1].itemid].itemId, missions[i].itemChange[1].quant);
      end

      text_Progresspercent[i].text = missions[i].conditionCount;
    end
  end
end
--endregion

--region Calculate
function UIActivityMonopoly.GetConditionComplete()
  return ActivityModel.PlayerAwardCount[ActivityModel.openActivityId * 1000 + 301].complete;
end

---當前已完成的圈數
function UIActivityMonopoly.GetCurrentRound(currentSteps)
	return math.modf(currentSteps / cellCount);
end

---當前所處的盤面位置
function UIActivityMonopoly.GetCurrentCellIndex(currentSteps)
  if currentSteps == 0 then
    return 1;
  end

	local result = math.fmod(currentSteps - 1,cellCount) + 1;
  log("CurrnetIndex： ".. result);
	return result;
end

---取得已經行走的總步數
function UIActivityMonopoly.GetCurrentSteps()
  if ActivityModel.PlayerAwardCount[ActivityModel.openActivityId * 1000 + 1] ~= nil then
    currentSteps = ActivityModel.PlayerAwardCount[ActivityModel.openActivityId * 1000 + 1].get  --任務的頁籤是千位數以上的數字 + 1則是表示第0頁的第一個任務
  end

	if currentSteps == nil then
		currentSteps = 0;
	end
  log("Current Total Steps: " .. currentSteps);
	return currentSteps;
end

function UIActivityMonopoly.GetCellData(cellIndex)
	return Cells[cellIndex];
end

function UIActivityMonopoly.GetAvailableRewardCount()
  local currentAwardData = ActivityModel.PlayerAwardCount[ActivityModel.openActivityId * 1000 + 301];
  return math.floor(this.GetConditionComplete() / currentAwardData.mission.conditionCount) - currentAwardData.get;
end
--endregion

--[[
	S:124-005 <特殊玩法結果> +Kind(1) +SubKind(4) +結果(1)
	Kind = 2: <大富翁>
	SubKind = 1: <擲骰子> + 結果(1)[0:成功+骰出數字(1) 1:失敗 2:非活動期間 3:太頻繁 4:貨幣不足 5:背包滿 255:功能未開放] + 獎勵任務ID(4)
	]]--

--region Protocol
function UIActivityMonopoly.ReceiveData(result, data)
  local message;
  if result == 0 then
		diceSteps = data:ReadByte();
    rewardMissionId = data:ReadInt32();
  elseif result == 1 then
    message = string.Get(80012); --失敗
  elseif result == 2 then
		message = string.Get(60065); --目前不是活動時間
  elseif result == 3 then
		message = string.Get(22841); --請稍後再點擊
  elseif result == 4 then
    message = string.format(string.Get(24934), ActivityModel.ActivityData[ActivityModel.openActivityId].coin[1].data.name); --%s(貨幣名) 不足
	elseif result == 5 then
    message = string.Get(23424); --請清理出兩格的背包空間後再來遊玩吞食大富翁，獎品好禮等著您唷！
	elseif result == 255 then
		message = string.Get(80332); --功能尚未開放
  end

	if result ~= 0 then
    ShowCenterMessage(message);
    isMoving = false;
    return
  end

	Network.Send(124, 2);
	Network.Send(124, 1);
  this.ProtocolCallBack();
end

function UIActivityMonopoly.ProtocolCallBack()
	currentSteps = this.GetCurrentSteps();
	currentRound = this.GetCurrentRound(currentSteps);
	currentCellIndex = this.GetCurrentCellIndex(currentSteps);

	if currentCellIndex == nil and currentRound == nil then
		currentCellIndex = 1;
		currentRound = 0;
	end
  log("dicePoint: " .. diceSteps);
	this.StartDiceEffect(diceSteps);
	local path = this.GetIndicatorPath(diceSteps);
	this.SetIndicatorPath(path, true);
	this.WaitCallBack = false;
end

function UIActivityMonopoly.ShowProgressRewardMessage()
  local award = ActivityModel.PlayerAwardCount[progressRewardMissionId];
  local rewardItemId = award.mission.itemChange[1].itemId;
  local rewardItemQuant = 0;
  if ActivityModel.ActivityData[ActivityModel.openActivityId].kind == activityKind.singleBar then
    rewardItemQuant = award.mission.itemChange[1].quant * (math.floor(this.GetConditionComplete() / award.mission.conditionCount) - progressRewardMissionGet);
  else
    rewardItemQuant = award.mission.itemChange[1].quant;
  end

  if rewardItemQuant == 0 then
		ShowCenterMessage(string.Get(98018)); --未能領取
  else
    this.ShowRewardMessage(rewardItemId, rewardItemQuant);
    this.ShowChatRewardMessage(rewardItemId, rewardItemQuant);
  end
  progressRewardMissionId = nil;
  progressRewardMissionGet = 0;
end
--endregion

--region DiceEffect
function UIActivityMonopoly.PlayRawImageEffect(rawImage, effectPage, effectInterval, callBack)
  rawImage:ShiftUV(1, effectPage, effectInterval);
  CGTimer.DoFunctionDelay(effectPage * effectInterval, callBack);
end

function UIActivityMonopoly.StartDiceEffect(diceResult)
  rawImage_RollingDice.uvRect = diceRollingEffectDefaultRect;
  rawImage_RollingDice.enabled = true;
  rawImage_StillDice.enabled = false;
  this.PlayRawImageEffect(uiRawImage_RollingDice, diceRollingEffectPage, diceEffectInterval,
    function()
      UIActivityMonopoly.EndDiceEffect(diceResult);
      UIActivityMonopoly.SetLeftStepText(diceResult);
      UIActivityMonopoly.StartMoveIndicator();
      end
  )
end

function UIActivityMonopoly.EndDiceEffect(diceResult)
  rawImage_RollingDice:StopShiftUV();
  rawImage_RollingDice.enabled = false;
  rawImage_StillDice.enabled = true;
  local result = math.clamp(diceResult, 1, diceResultImagePage);
  rawImage_StillDice.uvRect = diceUVRect[result];
end
--endregion

function UIActivityMonopoly.GetIndicatorPath(steps)
  if (steps <= 0) then
    return nil;
  end

  local path = Queue.New();
	local tempCell;

  for i = 1, steps do
		tempCell = currentCellIndex + i;
		if tempCell > cellCount then
			tempCell = tempCell - cellCount;
		end

    local nextCell = Cells[tempCell];
    path:Enqueue(nextCell);
  end

  return path;
end

--region CellDataRefresh 借用其他的福袋做為顯示使用
function UIActivityMonopoly.RefreshAllCells()
	local rewardIndex = 0;
  sortedActivtiyMdoelMission = ActivityModel.SortMission(ActivityModel.ActivityData[ActivityModel.openActivityId].page[4].missions);
	for _, v in pairs(sortedActivtiyMdoelMission) do
		if v.order ~= rewardIndex then
			rewardIndex = rewardIndex + 1;
      table.insert(originBlissBagData, (CONST_BLISSBAG_ITEM_ID + rewardIndex), this.DeepCopy(blissBagDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex]));
      table.insert(originItemData, (CONST_BLISSBAG_ITEM_ID + rewardIndex), this.DeepCopy(itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex]));
			blissBagDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex] = { items = {}, kindCount = 5};
    end
		local blissBagItem = {};
		blissBagItem.count = v.itemChange[1].quant;
		blissBagItem.itemId = v.itemChange[1].itemId;
		blissBagItem.kind = 1;
		blissBagItem.pr = v.conditionCount;
		table.insert(blissBagDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].items, blissBagItem);
		blissBagDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].kindCount = 1;
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].name = " ";
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].description = " ";
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].specialAbility = 48; --換成福袋
		itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].kind = 80;
		itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].element = 0;
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].btnState = 0; --開放福袋(不顯示未開放字樣)
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].iconId = 1497; --隨意固定的圖片，可任意更換
    itemDatas[CONST_BLISSBAG_ITEM_ID + rewardIndex].openUsed = 0; -- 使物品開放
	end

  for cellIndex = 1, cellCount do
		Cells[cellIndex].item:SetItem(CONST_BLISSBAG_ITEM_ID + cellIndex);
    table.insert(originTexture, cellIndex, Cells[cellIndex].item.rawImage_Icon)
    originTexture[cellIndex].texture = Cells[cellIndex].item.rawImage_Icon.texture;
    originTexture[cellIndex].color = Cells[cellIndex].item.rawImage_Icon.color;
    Cells[cellIndex].item.rawImage_Icon.texture = nil;
    Cells[cellIndex].item.rawImage_Icon.color = Color.Alpha;
  end
end
--endregion

--region Indicator
function UIActivityMonopoly.CheckIndicator()
  indicator.gameObject:SetActive(true);
  indicator:SetAnimationForceId(ERolePose.Stand);
  indicator:UpdateShow(false, 1);
end

function UIActivityMonopoly.IndicatorCrossCell(moveIndicator, targetCell)
  if moveIndicator.currentCell ~= nil then
    --this.TriggerCellEvent(moveIndicator.currentCell, EMonopolyCellEventTrigger.leave);
  end
  moveIndicator.currentCell = targetCell;
  --this.TriggerCellEvent(moveIndicator.currentCell, EMonopolyCellEventTrigger.enter);
end


function UIActivityMonopoly.StartMoveIndicator()
  if indicator == nil or indicator.movePath == nil then
    return
  end
  if indicator.target == nil then
    indicator.target = indicator.movePath:Dequeue();
  end
  if indicator.target ~= nil then
    this.CheckIndicator();
    this.SetIndicatorMoveFlag(true);
    indicator.step = 0;
  end
end

function UIActivityMonopoly.MoveIndicator(moveIndicator, moveDir, target)
  moveIndicator.transform.position = Vector3.Lerp(target.prevCell.go.transform.position, target.go.transform.position, moveIndicator.step / animationSteps);
  moveIndicator.position = Vector2.zero;
  moveDir = this.GetMapLocalPosition(moveDir);
  moveIndicator:FaceTo(moveDir, true);
  moveIndicator:SetAnimationForceId(ERolePose.Walk);
  moveIndicator:UpdateShow(false, 1);
end

function UIActivityMonopoly.IndicatorReachTarget(moveIndicator, targetPosition)
  moveIndicator.transform.position = targetPosition;
  local target = moveIndicator.target;
  moveIndicator.target = moveIndicator.movePath:Dequeue();
  if moveIndicator.target == nil then
    this.EndMoveIndicator(moveIndicator);
  else
    moveIndicator.step = 0;
    this.UpdateLeftStep();
  end
end

function UIActivityMonopoly.ShowRewardMessage(itemId, quant)
  UICenterMessage.Clear();
    local message = string.format(string.Get(23426), itemDatas[itemId].name, quant); --恭喜獲得%s %d 個
    ShowCenterMessage(message);
end

function UIActivityMonopoly.ShowChatRewardMessage(itemId, quant)
    local message = string.format(string.Get(21179), itemDatas[itemId].name, quant); --獲得 【 %s 】 * %s
    Chat.AddMessage(EChannel.System, message);
end

function UIActivityMonopoly.EndMoveIndicator(moveIndicator)
  moveIndicator.step = nil;
  moveIndicator:SetAnimationForceId(ERolePose.Stand);
  moveIndicator:UpdateShow(false, 1);
  moveIndicator.rawImage_Body.enabled = true;
  rawImage_StillDice.enabled = false;
  isMoving = false;
  if rewardMissionId == nil then return end;
  local rewardItemId = ActivityModel.PlayerAwardCount[rewardMissionId].mission.itemChange[1].itemId;
  local rewardItemQuant = ActivityModel.PlayerAwardCount[rewardMissionId].mission.itemChange[1].quant;
  this.ShowRewardMessage(rewardItemId, rewardItemQuant);
  this.ShowChatRewardMessage(rewardItemId, rewardItemQuant);
  rewardMissionId = nil;
  this.HideLeftStep();
	this.GetCurrentSteps();
	this.GetCurrentCellIndex(currentSteps);
  if autoRoll == true then
    this.OnClick_RollDice();
  end
end

function UIActivityMonopoly.TeleportIndicator(cellIndex)
  if indicator == nil or cellIndex == nil then
    return
  end
  indicator.target, indicator.movePath = nil;
  local targetCell = this.GetCellData(cellIndex);
  indicator.transform.position = targetCell.go.transform.position;
  this.IndicatorCrossCell(indicator, targetCell);

  indicator:FaceTo(targetCell.defaultDirection, true);
  this.CheckIndicator();
  this.EndMoveIndicator(indicator);
end
--endregion

--region view
function UIActivityMonopoly.SetLeftStepText(steps)
  if steps == nil then return end
  text_IndicatorLeftSteps.text = steps;
end

function UIActivityMonopoly.HideLeftStep()
  text_IndicatorLeftSteps.text = "";
end

function UIActivityMonopoly.ForceSetIndicatorPosition(offset)
  indicator.transform.position = indicator.transform.position + offset;
end
--endregion

--region Clear
function UIActivityMonopoly.Clear()
  if slider_ProgressBarOne ~= nil and slider_ProgressBarTwo ~= nil then
    slider_ProgressBarOne.value = 0;
    slider_ProgressBarTwo.value = 0;
  end

  if text_CurrentCount then
    text_CurrentCount.text = "";
  end

  if text_ProgressCount then
    text_ProgressCount.text = "";
  end

  CGTimer.RemoveListener(this.Update);
  currentSteps = 0;
  currentCellIndex = 0;
  currentRound = 0;

  autoRoll = false;
  rewardMissionId = nil;
  lastClickRollTime = nil;
  lastClickProgessPrizeTime = nil;
  UIActivityMonopoly.ResetBorrowData();
  originBlissBagData = {};
  originItemData = {};
  originTexture = {};
end
--endregion

function UIActivityMonopoly.DeepCopy(original)
  local copy = {};
  if original == nil then
    return copy;
  end

  for k, v in pairs(original) do
      if type(v) == "table" then
          copy[k] = UIActivityMonopoly.DeepCopy(v);
      else
          copy[k] = v;
      end
  end
  return copy;
end