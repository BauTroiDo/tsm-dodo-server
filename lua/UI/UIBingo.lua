EBingoActKind = {
  fiveSquareSingleBar = 6,
  fiveSquareMultiBar = 15,
}

UIBingo = {}
local this = UIBingo;

this.name = "UIBingo";
this.uiController = nil;

--Const
local CONST_BINGO_BOARD_LENGTH = 5; --盤面長寬
local CONST_BINGO_MULTIAWARD_COUNT = 3; --多獎勵進度條的獎勵數量
local CONST_MISSION_INDEX = 1000; --任務Id
local CONST_MISSION_PAGE_INDEX = 300; --任務的第三頁

--Variable
local bingoRewardGroup = {};
local bingoBoardGroup = {};
local missionSortedData = {};
local percents = {  --多獎勵進度條的物件對應名稱
  33,
  66,
  100,
}

local progressBarType = {
  Once = 1,
  Multiple = 2,
}

local gameObject_BoardRoot;
local gameObject_ProgressBarOne;
local gameObject_ProgressBarTwo;
local gameObject_ProgressBarOne_ItemRoot;
local gameObject_ProgressBarTwo_ItemRoot = {};

local rawImage_BingoPic;
local image_BarSingle_Border;
local image_BarSingle_White;
local image_BarMultiple_Items = {};
local image_BarMultiple_Hints = {};

local text_ProgressCount;
local text_CurrentCount;
local text_AwardCount;
local text_FlipBingo;
local text_Progresspercent;

local slider_ProgressBarSingle;
local slider_ProgressBarMultiple;

local progressBarKind;
local currentCharge; --當前翻牌次數
local totalCharge;   --進度條滿需要的翻牌數字

local bingoMaxPrizeCount = CONST_BINGO_BOARD_LENGTH * 2 + 2; --任務的獎勵總數
local bingoBoardCount = CONST_BINGO_BOARD_LENGTH * CONST_BINGO_BOARD_LENGTH; --任務的盤面總數
local multipleActivityId;
local singleActivityId;
local lastClickFlipTime = nil;
local lastClickPrizeTime = nil;
local lastClickProgessTime = nil;
local clickResult = false;
local CLICK_DELAY = 1 -- 擋頻繁點擊1秒(與S同步)
local bingoUIPage = 3;
local isInitBingo = false;

function UIBingo.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;



  for i = 1, bingoMaxPrizeCount do
    bingoRewardGroup[i] = {};
  end

  for i = 1, CONST_BINGO_BOARD_LENGTH do
    bingoRewardGroup[i].prizeImage = uiController:FindImage(string.Concat("Image_BingoAwardGrid_L", i));
    bingoRewardGroup[i + 5].prizeImage = uiController:FindImage(string.Concat("Image_BingoAwardGrid_B", i));
  end
  bingoRewardGroup[11].prizeImage = uiController:FindImage("Image_BingoAwardGrid_DR");
  bingoRewardGroup[12].prizeImage = uiController:FindImage("Image_BingoAwardGrid_DL");

  for i = 1, bingoMaxPrizeCount do
    bingoRewardGroup[i].claimed = false;
    bingoRewardGroup[i].isLock = true;
    bingoRewardGroup[i].item = UIItemController.New(bingoRewardGroup[i].prizeImage.transform:Find("ItemRoot").transform);
    bingoRewardGroup[i].border = bingoRewardGroup[i].prizeImage.transform:Find("Image_Border"):GetComponent("Image");
    bingoRewardGroup[i].border.gameObject:SetActive(false);
    bingoRewardGroup[i].lock = bingoRewardGroup[i].prizeImage.transform:Find("Image_Lock"):GetComponent("Image");
    bingoRewardGroup[i].grey = bingoRewardGroup[i].prizeImage.transform:Find("Image_Grey"):GetComponent("Image");
    bingoRewardGroup[i].white = bingoRewardGroup[i].prizeImage.transform:Find("Image_White"):GetComponent("Image");
    bingoRewardGroup[i].white.gameObject:SetActive(false);
    bingoRewardGroup[i].event = bingoRewardGroup[i].prizeImage.gameObject:GetComponent("UIEvent");
    bingoRewardGroup[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_BingoPrize);
    bingoRewardGroup[i].event.parameter = i;
  end

  gameObject_ProgressBarOne = uiController:FindGameObject("ProgressBarOne");
  slider_ProgressBarSingle = uiController:FindSlider("Slider_ProgressBarOne");
  gameObject_ProgressBarTwo = uiController:FindGameObject("ProgressBarTwo");
  slider_ProgressBarMultiple = uiController:FindSlider("Slider_ProgressBarTwo");
  gameObject_BoardRoot = uiController:FindGameObject("Image_SubBG");
  image_BarSingle_Border = uiController:FindImage("Image_AwardBar_Border");
  image_BarSingle_White = uiController:FindImage("Image_AwardBar_White");
  image_BarSingle_Border.gameObject:SetActive(false);
  image_BarSingle_White.gameObject:SetActive(false);
  this.SetProgressBar()
  gameObject_ProgressBarOne:SetActive(progressBarKind == progressBarType.Once);
  gameObject_ProgressBarTwo:SetActive(progressBarKind == progressBarType.Multiple);

  for i = 1, CONST_BINGO_MULTIAWARD_COUNT do
    image_BarMultiple_Items[i] = {};
  end

  for i = 1, CONST_BINGO_MULTIAWARD_COUNT do
    text_Progresspercent = uiController:FindText(string.Concat("Text_percentage", percents[i]));
    image_BarMultiple_Items[i].image = uiController:FindImage(string.Concat("Image_Award_", percents[i],"percent"));
    image_BarMultiple_Items[i].lock = image_BarMultiple_Items[i].image.transform:Find("Image_Lock"):GetComponent("Image");
    image_BarMultiple_Items[i].border = uiController:FindImage(string.Concat("Image_", percents[i], "percent_Border"));
    image_BarMultiple_Items[i].white = uiController:FindImage(string.Concat("Image_", percents[i],"percent_White"));
    image_BarMultiple_Items[i].border.gameObject:SetActive(false);
    image_BarMultiple_Items[i].white.gameObject:SetActive(false);
    image_BarMultiple_Items[i].event = uiController:FindEvent(string.Concat("Image_Award_", percents[i],"percent"))
    image_BarMultiple_Items[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_MultiProgressPrize);
    image_BarMultiple_Items[i].event.parameter = i;
    table.insert(image_BarMultiple_Hints, uiController:FindImage(string.Concat("Image_percentage", percents[i])));
  end

  rawImage_BingoPic = uiController:FindRawImage("RawImage_BingoPic");

  text_ProgressCount = uiController:FindText("Text_ProgressCount");
  text_CurrentCount = uiController:FindText("Text_CurrentCount");
  text_FlipBingo = uiController:FindText("Text_FlipBingo");
  text_AwardCount = uiController:FindText("Text_AwardCount");
  text_FlipBingo.text = string.Get(24930); --@賓果@

  for i = 1, bingoBoardCount do
    bingoBoardGroup[i] = {};
    bingoBoardGroup[i].flipped = false;
    bingoBoardGroup[i].checked = false;
    bingoBoardGroup[i].boardImage = uiController:FindImage(string.Concat("Image_BingoGrid_", math.fmod((i - 1) , 5) + 1, "_", (6 - math.ceil(i / 5))));
    bingoBoardGroup[i].ball = bingoBoardGroup[i].boardImage.transform:Find("Image_RedBall"):GetComponent("Image");
  end

  local tempEvent = uiController:FindEvent("Image_Bingo");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_BingoFlip);
  tempEvent = uiController:FindEvent("Image_Award")
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_OnceProgressPrize);
end

--[[
  S:124-005 <賓果玩法結果> +Kind(1)[1:賓果玩法] +SubKind(4)[1:賓果 2:換盤] +結果(1)
  當SubKind = 1[賓果] :結果[0:成功 1:失敗 2:貨幣不足 3:背包滿]
  　SubKind = 2[換盤] :結果[0:成功 1:失敗 2:有獎勵未領]
]]--
function UIBingo.HandleProtocol(SubKind, Result)
  if SubKind == 1 then
    if Result == 0 then
      Network.Send(124, 1);
    elseif Result == 1 then
      ShowCenterMessage(string.Get(80012)); --失敗
    elseif Result == 2 then
      ShowCenterMessage(string.Get(20277)); --未到開放時段
    elseif Result == 3 then
      ShowCenterMessage(string.Get(22841)); --請稍後再點擊
    elseif Result == 4 then
      ShowCenterMessage(string.format(string.Get(24934), ActivityModel.ActivityData[ActivityModel.openActivityId].coin[1].data.name)); --貨幣不足
    elseif Result == 5 then
      ShowCenterMessage(string.Get(24456)); --背包空間滿了，請清出一格以上的空間再進行遊玩
    elseif Result == 255 then
      ShowCenterMessage(string.Get(80332)); --功能尚未開放
    else return end;
  elseif SubKind == 2 then
    if Result == 0 then
      this.ChangeBoard();
    elseif Result == 1 then
      ShowCenterMessage(string.Get(80012)); --失敗
    elseif Result == 2 then
      ShowCenterMessage(string.Get(20277)); --未到開放時段
    elseif Result == 3 then
      ShowCenterMessage(string.Get(22841)); --請稍後再點擊
    elseif Result == 4 then
      ShowCenterMessage(string.Get(24933)); --尚有獎勵未領取
      text_FlipBingo.text = string.Get(24932); --@換盤@
    elseif Result == 255 then
      ShowCenterMessage(string.Get(80332)); --功能尚未開放
    else return end;
  else
    return;
  end
end

function UIBingo.SetMissionAward()
  missionSortedData = ActivityModel.SortMission(ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions);
  local isBingoAct;
  for k, v in pairs(missionSortedData) do
    isBingoAct = (ActivityModel.ActivityData[ActivityModel.openActivityId].kind == EBingoActKind.fiveSquareSingleBar) or (ActivityModel.ActivityData[ActivityModel.openActivityId].kind == EBingoActKind.fiveSquareMultiBar);
    if v.order > bingoMaxPrizeCount and isBingoAct then
      table.remove(missionSortedData, k);
    end
  end

  for i = 1, bingoMaxPrizeCount do
    bingoRewardGroup[i].mission = missionSortedData[i];
    bingoRewardGroup[i].item:SetItem(bingoRewardGroup[i].mission.itemChange[1].itemId, bingoRewardGroup[i].mission.itemChange[1].quant);
  end
end

function UIBingo.OnOpen()
  gameObject_BoardRoot:SetActive(true);
  this.InitData(bingoUIPage);
  this.SetMissionAward();
  this.SetProgressBar();
  gameObject_ProgressBarOne:SetActive(progressBarKind == progressBarType.Once);
  gameObject_ProgressBarTwo:SetActive(progressBarKind == progressBarType.Multiple);
  this.UpdateProgressBarCount();
  this.UpdateBoard();
  this.CheckChangeBoard();
end

function UIBingo.OnClose()
  if gameObject_BoardRoot ~= nil then
    gameObject_BoardRoot:SetActive(false);
    this.Clear();
  end
end

function UIBingo.OnClick_BingoPrize(event)
  clickResult, lastClickPrizeTime = this.CheckClickTime(lastClickPrizeTime);
  if not clickResult then return end;
  local index = event.parameter;
  local missionId = missionSortedData[index].id;

  sendBuffer:Clear();
  sendBuffer:WriteUInt32(missionId);
  sendBuffer:WriteUInt32(1);
  Network.Send(124, 3 , sendBuffer);
end

--[[
  C:124-005 <進行特殊玩法> +活動ID(1) +Kind(1)
	Kind1: +SubKind(4)[1:賓果 2:換盤] +次數(4)
]]--
function UIBingo.OnClick_BingoFlip(event)
  clickResult, lastClickFlipTime = this.CheckClickTime(lastClickFlipTime);
  if not clickResult then return end;
  local SubKind, Count = this.CheckChangeBoard();
  sendBuffer:Clear();
  sendBuffer:WriteByte(ActivityModel.ActivityData[ActivityModel.openActivityId].activityId);
  sendBuffer:WriteByte(ESpecialActivity.Bingo);
  sendBuffer:WriteInt32(SubKind); --賓果
  sendBuffer:WriteInt32(Count); --次數

  Network.Send(124, 5, sendBuffer);
end

function UIBingo.OnClick_OnceProgressPrize()
  clickResult, lastClickProgessTime = this.CheckClickTime(lastClickProgessTime);
  if not clickResult then return end;
  sendBuffer:Clear();
  sendBuffer:WriteUInt32(this.ProgressBarMission(EBingoActKind.fiveSquareSingleBar) + bingoMaxPrizeCount + 1);
  local result = math.floor(currentCharge / totalCharge);
  sendBuffer:WriteUInt32(result);
  Network.Send(124, 3 , sendBuffer);
end

function UIBingo.OnClick_MultiProgressPrize(event)
  clickResult, lastClickProgessTime = this.CheckClickTime(lastClickProgessTime);
  if not clickResult then return end;
  sendBuffer:Clear();
  sendBuffer:WriteUInt32(this.ProgressBarMission(EBingoActKind.fiveSquareMultiBar) + bingoMaxPrizeCount + event.parameter);
  sendBuffer:WriteUInt32(1);
  Network.Send(124, 3 , sendBuffer);
end

function UIBingo.UpdateBoard()
  local missionPattern;
  local boardPattern;
  local bingoBoardPlayerAwardCount = {};
  local bingoAwardPlayerAwardCount = {};
  missionPattern = string.Concat("^", tostring(ActivityModel.openActivityId),"3%d%d$"); --match使用的pattern ^表達匹配開始 .. ActivityModel.openActivityId .. 3 .. %d%d
  boardPattern = string.Concat("^", tostring(ActivityModel.openActivityId),"0%d%d$"); --match使用的pattern ^表達匹配開始 .. ActivityModel.openActivityId .. 0 ..%d%d

  for k, v in pairs(ActivityModel.PlayerAwardCount) do
    local str = tostring(k)
    local result;
    if string.match(str, boardPattern) then
      result = tonumber(string.sub(str, -2)); --從字符串 str 中提取出最後兩個字符，然後將這兩個字符轉換為數字。
      if result <= 25 then
        bingoBoardPlayerAwardCount[result] = v;
      end

      if result <= 37 and result > 25 then
        bingoAwardPlayerAwardCount[result - 25] = v;
      end
    end

    if string.match(str, missionPattern) then --match(要匹配的目標字串, 匹配規則pattern) 根據查詢結果的有無回傳bool
      result = tonumber(string.sub(str, -2));
      if result == bingoMaxPrizeCount + 1 and progressBarKind == progressBarType.Once then -- 單一Bar條
        if v.get < v.mission.getLimit and v.mission.getLimit ~= 0 then
          image_BarSingle_Border.gameObject:SetActive(true);
          image_BarSingle_White.gameObject:SetActive(false);
        elseif v.get >= v.mission.getLimit and v.mission.getLimit ~= 0 then
            image_BarSingle_Border.gameObject:SetActive(false);
            image_BarSingle_White.gameObject:SetActive(true);
        elseif v.mission.getLimit == 0 then
          if v.complete >= v.mission.conditionCount then
            if (math.floor(v.complete / v.mission.conditionCount) - v.get) > 0 then
              image_BarSingle_Border.gameObject:SetActive(true);
              image_BarSingle_White.gameObject:SetActive(false);
              text_AwardCount.text = string.Concat("x", tostring(math.floor(v.complete / v.mission.conditionCount) - v.get));
            else
              image_BarSingle_Border.gameObject:SetActive(false);
              image_BarSingle_White.gameObject:SetActive(true);
              text_AwardCount.text = "";
            end
          end
        end
      elseif result > bingoMaxPrizeCount and progressBarKind == progressBarType.Multiple then
        if v.get < v.mission.getLimit and v.mission.getLimit ~= 0 and currentCharge < v.mission.conditionCount then
          image_BarMultiple_Items[result - bingoMaxPrizeCount].lock.gameObject:SetActive(true);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].lock.raycastTarget = false;
          image_BarMultiple_Items[result - bingoMaxPrizeCount].border.gameObject:SetActive(false);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].white.gameObject:SetActive(false);
        elseif v.get < v.mission.getLimit and v.mission.getLimit ~= 0 and currentCharge >= v.mission.conditionCount then
          image_BarMultiple_Items[result - bingoMaxPrizeCount].lock.gameObject:SetActive(false);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].border.gameObject:SetActive(true);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].white.gameObject:SetActive(false);
        elseif v.get >= v.mission.getLimit and v.mission.getLimit ~= 0 then
          image_BarMultiple_Items[result - bingoMaxPrizeCount].lock.gameObject:SetActive(false);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].border.gameObject:SetActive(false);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].white.gameObject:SetActive(true);
          image_BarMultiple_Items[result - bingoMaxPrizeCount].white.raycastTarget = false;
        end
      end
    end
  end

  for k, v in pairs(bingoBoardPlayerAwardCount) do
    bingoBoardGroup[k].flipped = (v.get ~= 0);
  end

  for k, v in pairs(bingoAwardPlayerAwardCount) do
    bingoRewardGroup[k].claimed = (v.get == 1);
  end

  for i = 1, bingoBoardCount do
      if bingoBoardGroup[i].flipped then
        --log(string.Concat("賓果座標index: ", i));
        bingoBoardGroup[i].boardImage.sprite = UI.GetSprite("bod172");
        bingoBoardGroup[i].ball.sprite = UI.GetSprite("com143");
        if not bingoBoardGroup[i].checked then
          this.CheckBingo(i);
          bingoBoardGroup[i].checked = true;
        end
      else
        bingoBoardGroup[i].boardImage.sprite = UI.GetSprite("bod036");
        bingoBoardGroup[i].ball.sprite = UI.GetSprite("com144");
      end
  end

  for _, v in pairs(bingoRewardGroup) do
    if v.claimed then
      v.grey.gameObject:SetActive(false);
      v.lock.gameObject:SetActive(false);
      v.border.gameObject:SetActive(true);
      v.white.gameObject:SetActive(true);
      v.border.raycastTarget = false;
    else
      if v.isLock == false then
        v.grey.gameObject:SetActive(false);
        v.lock.gameObject:SetActive(false);
        v.border.gameObject:SetActive(true);
      end
    end
  end

  this.UpdateProgressBarCount();
end

function UIBingo.ChangeBoard()
  for i = 1, bingoBoardCount do
    if bingoBoardGroup[i].flipped then
      bingoBoardGroup[i].boardImage.sprite = UI.GetSprite("bod036");
      bingoBoardGroup[i].ball.sprite = UI.GetSprite("com144");
      bingoBoardGroup[i].flipped = false;
      bingoBoardGroup[i].checked = false;
    end
  end

  for _, v in pairs(bingoRewardGroup) do
    if v.claimed then
      v.claimed = false;
    end
    v.isLock = true;
    v.border.raycastTarget = true;
    v.white.gameObject:SetActive(false);
    v.border.gameObject:SetActive(false);
    v.lock.gameObject:SetActive(true);
    v.grey.gameObject:SetActive(true);
  end

  text_FlipBingo.text = string.Get(24930); --@賓果@
  Network.Send(124, 1);
end

--Shape = this.Shape
function UIBingo.SetProgressBar()
  local kind = ActivityModel.ActivityData[ActivityModel.openActivityId].kind;
  if kind == EBingoActKind.fiveSquareSingleBar then
    progressBarKind = progressBarType.Once;
  elseif kind == EBingoActKind.fiveSquareMultiBar then
    progressBarKind = progressBarType.Multiple;
  end
end

function UIBingo.SetBingo(page)
  if not isInitBingo then
    isInitBingo = true;
    local rootObj, selfPrefab= UIActivityModel.GetUIActivityModelRoot(ESpecialActivity.Bingo);
    local go = rootObj.Instantiate(selfPrefab, rootObj.transform);
    go.name = "UI_Bingo5X5";
    this.Initialize(go);
    this.InitData(page);
  elseif page == 3 and ActivityModel.CheckCurrentSpecialActivity(ESpecialActivity.Bingo) then
    this.OnOpen();
  else
    this.OnClose();
  end
end

function UIBingo.CheckBingo(index)
  local row = math.ceil(index / 5);
  local col = math.fmod((index - 1), 5) + 1;
  local Complete = true;
  local idx;
  for i = 1, 5 do
    idx = (row - 1) * 5 + i
    if not bingoBoardGroup[idx].flipped then
        Complete = false;
        break
    end
  end
  if Complete then
    bingoRewardGroup[row].isLock = false;
  end

  Complete = true
  for i = 0, 4 do
    idx = i * 5 + col
    if not bingoBoardGroup[idx].flipped then
        Complete = false;
        break
    end
  end
  if Complete then
    bingoRewardGroup[col + 5].isLock = false;
  end

  Complete = true
  if math.fmod(index, 6) == 1 then
      for i = 0, 4 do
          idx = i * 6 + 1
          if not bingoBoardGroup[idx].flipped then
              Complete = false;
              break
          end
      end
  else
      Complete = false;
  end
  if Complete then
    bingoRewardGroup[11].isLock = false;
  end

  Complete = true;
  if math.fmod((index - 1), 4) == 0 and index ~= 1 and index ~= 25 then
      for i = 0, 4 do
          idx = i * 4 + 5
          if not bingoBoardGroup[idx].flipped then
              Complete = false;
              break
          end
      end
  else
      Complete = false;
  end
  if Complete then
    bingoRewardGroup[12].isLock = false;
  end
end

function UIBingo.CheckChangeBoard()
  local count = 0;
  local result;
  for _, v in pairs(bingoRewardGroup) do
    if not v.isLock then
      count = count + 1;
    end
  end

  if count >= 3 then
    result = 2;
    text_FlipBingo.text = string.Get(24932); --@換盤@
  else
    result = 1;
    text_FlipBingo.text = string.Get(24930); --@賓果@
  end

  return result, 1;
end

function UIBingo.ProgressBarMission(progressBarType)
  local progressBarMission;
  if progressBarType == EBingoActKind.fiveSquareSingleBar then
    progressBarMission = singleActivityId * CONST_MISSION_INDEX + CONST_MISSION_PAGE_INDEX;
  elseif progressBarType == EBingoActKind.fiveSquareMultiBar then
    progressBarMission = multipleActivityId * CONST_MISSION_INDEX + CONST_MISSION_PAGE_INDEX;
  end
  return progressBarMission;
end

function UIBingo.UpdateProgressBarCount()
  local progressBarKind;
  local once = (progressBarKind == progressBarType.Once);
  local barAwardMission = ActivityModel.PlayerAwardCount[ActivityModel.openActivityId * CONST_MISSION_INDEX + CONST_MISSION_PAGE_INDEX + bingoMaxPrizeCount + 1];

  if ActivityModel.openActivityId == singleActivityId then progressBarKind = EBingoActKind.fiveSquareSingleBar;
  elseif ActivityModel.openActivityId == multipleActivityId then progressBarKind = EBingoActKind.fiveSquareMultiBar end

  currentCharge = ActivityModel.PlayerAwardCount[this.ProgressBarMission(progressBarKind) + bingoMaxPrizeCount + 1].complete;
  text_CurrentCount.text = currentCharge;

  if once then
    for k, v in pairs(ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions) do
      if v.order == (bingoMaxPrizeCount + 1) then
        totalCharge = ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions[k].conditionCount;
        slider_ProgressBarSingle.maxValue = totalCharge;
        text_AwardCount.text = string.Concat("x", tostring(math.floor(barAwardMission.complete / barAwardMission.mission.conditionCount) - barAwardMission.get));
      end
    end
    text_ProgressCount.text = totalCharge;
  else
    local sortedMission = ActivityModel.ActivityData[ActivityModel.openActivityId].page[3].missions;
		table.sort(sortedMission, function(a, b) return a.order > b.order end)
    totalCharge = sortedMission[1].conditionCount;
    slider_ProgressBarMultiple.maxValue = totalCharge;
    text_ProgressCount.text = totalCharge;
    for i = 1, CONST_BINGO_MULTIAWARD_COUNT do
      text_Progresspercent.text = ActivityModel.PlayerAwardCount[this.ProgressBarMission(EBingoActKind.fiveSquareMultiBar) + bingoMaxPrizeCount + i].mission.conditionCount;
    end
  end

  local progress;
  progress = math.fmod(currentCharge, totalCharge);
  slider_ProgressBarSingle.value = progress;

  if currentCharge < totalCharge then
    slider_ProgressBarMultiple.value = math.fmod(currentCharge, totalCharge);
  else
    slider_ProgressBarMultiple.value = slider_ProgressBarMultiple.maxValue;
  end
end

function UIBingo.IsVisible()
	if gameObject_BoardRoot ~= nil then
    return gameObject_BoardRoot.activeSelf;
  else
    return false;
  end
end

function UIBingo.InitData(page)
  local index = 1;
  local chargeHint = {};
  for _, v in pairs(ActivityModel.ActivityData) do
    if v.kind == EBingoActKind.fiveSquareSingleBar then
      if v.activityId == ActivityModel.openActivityId then
        singleActivityId = v.activityId;
        for _, n in pairs(v.page[3].missions) do
          if n.order > bingoMaxPrizeCount then
            gameObject_ProgressBarOne_ItemRoot = UIItemController.New(this.uiController:FindImage("Image_Award").transform:Find("ItemRoot").transform);
            gameObject_ProgressBarOne_ItemRoot:SetItem(n.itemChange[1].itemId, n.itemChange[1].quant);
          end
        end
        break;
      else
        singleActivityId = 0;
      end
    elseif v.kind == EBingoActKind.fiveSquareMultiBar then
      if v.activityId == ActivityModel.openActivityId then
        multipleActivityId = v.activityId;
        local items = {};
        for _, n in pairs(v.page[3].missions) do
          if n.order > bingoMaxPrizeCount then
            table.insert(items, n);
          end
        end
        table.sort(items, function(a, b) return a.order < b.order end);

        for _, m in pairs(items) do
          table.insert(gameObject_ProgressBarTwo_ItemRoot, UIItemController.New(this.uiController:FindImage(string.Concat("Image_Award_", percents[index], "percent")).transform:Find("ItemRoot").transform));
          gameObject_ProgressBarTwo_ItemRoot[index]:SetItem(m.itemChange[1].itemId, m.itemChange[1].quant);
          table.insert(chargeHint, m.conditionCount);
          index = index + 1;
        end
        break;
      else
        multipleActivityId = 0;
      end
    end
  end

  if table.Count(chargeHint) >= CONST_BINGO_MULTIAWARD_COUNT then
    table.sort(chargeHint);
    local height = slider_ProgressBarMultiple.fillRect.rect.height;
    local piece = height / chargeHint[3];
    for i = 1, CONST_BINGO_MULTIAWARD_COUNT do
      image_BarMultiple_Hints[i].rectTransform.anchoredPosition = Vector2.New(-44.6, -(height / 2) + piece * chargeHint[i]); --44.6為prefab上取來的X軸數字
    end
  end

  this.SetMissionAward();
	this.UpdateProgressBarCount();
  this.UpdateBoard();
  if page ~= 3 then
    this.OnClose();
  end
end

function UIBingo.Clear()
  if table.Count(bingoRewardGroup) ~= 0 then
    for i = 1, bingoMaxPrizeCount do
      bingoRewardGroup[i].claimed = false;
      bingoRewardGroup[i].isLock = true;
      bingoRewardGroup[i].border.raycastTarget = true;
      bingoRewardGroup[i].white.gameObject:SetActive(false);
      bingoRewardGroup[i].border.gameObject:SetActive(false);
      bingoRewardGroup[i].lock.gameObject:SetActive(true);
      bingoRewardGroup[i].grey.gameObject:SetActive(true);
    end
  end

  if table.Count(bingoBoardGroup) ~= 0 then
    for i = 1, bingoBoardCount do
      bingoBoardGroup[i].flipped = false;
      bingoBoardGroup[i].checked = false;
    end
  end

  if slider_ProgressBarSingle ~= nil and slider_ProgressBarMultiple ~= nil then
    slider_ProgressBarSingle.value = 0;
    slider_ProgressBarMultiple.value = 0;
  end

  lastClickFlipTime = nil;
  lastClickPrizeTime = nil;
  lastClickProgessTime = nil;
end

function UIBingo.CheckClickTime(lastClickTime)
  local currentTime = CGTimer.serverTime;
  if lastClickTime then
    local timeDifference = currentTime:Subtract(lastClickTime)
    if timeDifference.TotalSeconds < CLICK_DELAY then
        ShowCenterMessage(string.Get(22841)) --請稍後再點擊
        return false , lastClickTime
    end
  end

  lastClickTime = currentTime
  return true, lastClickTime;
end