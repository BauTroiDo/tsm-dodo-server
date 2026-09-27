
UILeaderboardSimple = {}
local this = UILeaderboardSimple;

this.name = "UILeaderboardSimple";
this.uiController = nil;
this.pageRawAmount = 6;

--ui
local transform_FrameRect;

local text_LadderTitle;
local text_Ranking;
local text_Score;
local text_Page;
local text_Empty;
local text_Claim;
local text_CalcScore;
local image_Reward;
local image_CalcScoreMask;

local scrollContent_LeaderboardSimple;
local scrollItems_LeaderboardSimple = {};

--const
local maxInfo = 6;

--var
local currentPage = 1;
local currentBoardKind = 0;
local currentLeaderboardId = 0;
local board;
local totalPage = 1;
local lastClickRefreshTime = nil;
local CLICK_DELAY = 30;
local clickResult = false;

function UILeaderboardSimple.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  --this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect"):GetComponent("RectTransform");
  transform_FrameRect.localScale = Vector3.New(0.8, 0.8, 1);

  text_LadderTitle = uiController:FindText("Text_LadderTitle");
  text_Ranking = uiController:FindText("Text_Ranking");
  text_Score = uiController:FindText("Text_Score");
  text_Page = uiController:FindText("Text_Page");
  text_Empty = uiController:FindText("Text_Empty");
  text_Claim = uiController:FindText("Text_Claim");
  text_CalcScore = uiController:FindText("Text_CalcScore");
  image_Reward = uiController:FindImage("Image_Reward");
  image_CalcScoreMask = uiController:FindImage("Image_CalcScoreMask");

  scrollContent_LeaderboardSimple = uiController:FindScrollContent("ScrollContent_Leaderboard");
  scrollContent_LeaderboardSimple.onInitialize = this.OnInitialize_scrollContent_LeaderboardSimple;
  scrollContent_LeaderboardSimple.onItemChange = this.OnItemChange_scrollContent_LeaderboardSimple;
  scrollContent_LeaderboardSimple:Initialize(1);

  text_CalcScore.text = string.Get(25027); --結算中
  local tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PrevPage);

  tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_NextPage);

  tempEvent = uiController:FindEvent("Image_Reward");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.Onclick_ClaimReward);
end

function UILeaderboardSimple.OnOpen(boardKind, leaderboardId)
  UIActivityModel.Subscribe(function()
    image_CalcScoreMask.gameObject:SetActive(true)
  end);
  log("UILeaderboardSimple.OnOpen: leaderboardId: " .. leaderboardId);
  this.SendRequest(boardKind, leaderboardId);
	local playerCount = LeaderboardSimpleData.GetBoardPlayersCount(boardKind, leaderboardId);
  local rank = LeaderboardSimpleData.GetRank(boardKind, leaderboardId);
  local score = LeaderboardSimpleData.GetSelfScore(boardKind, leaderboardId);
  board = LeaderboardSimpleData.GetSelfBoard(boardKind, leaderboardId);

  text_Empty.gameObject:SetActive(true);
  currentBoardKind = boardKind;
  currentLeaderboardId = leaderboardId;

  if playerCount > 0 then
    scrollContent_LeaderboardSimple:Reset(playerCount)
  end

  UIActivityModel.SetOnClick_Refresh(this.OnClick_Refresh);
  this.UpdateUI(leaderboardId, math.ceil(rank / UILeaderboardSimple.pageRawAmount), math.ceil( playerCount / this.pageRawAmount), rank, score, board.players);
  if not CGTimer.ContainsListener(this.UpdateBoard) then
    CGTimer.AddListener(this.UpdateBoard, 180, true);
  end
  return true;
end

function UILeaderboardSimple.OnClose()
  UI.Close(UILeaderboardSimple);
  UIActivityModel.SetOnClick_Refresh(UIActivityModel.OnClick_Refresh);
  if CGTimer.ContainsListener(this.UpdateBoard) then
    CGTimer.RemoveListener(this.UpdateBoard);
  end
  return true;
end

function UILeaderboardSimple.OnVisible(visible)
  if visible then
    this.SendRequest(currentBoardKind, currentLeaderboardId);
  end
end

function UILeaderboardSimple.SendRequest(boardKind, leaderboardId)
  --C:126-001 <問分組排行榜目前分數> +排行榜Kind(1) +排行榜ID(2)
  boardKind = boardKind or 0;
  leaderboardId = leaderboardId or 0;
  if boardKind == 0 or leaderboardId == 0 then
    return;
  end

  log("版子種類: " .. boardKind);
  log("版子ID: " .. leaderboardId)
  sendBuffer:Clear();
  sendBuffer:WriteByte(boardKind);
  sendBuffer:WriteUInt16(leaderboardId);
  Network.Send(126, 1, sendBuffer);
end

function UILeaderboardSimple.GetCurrentOpenBoard()
  return currentBoardKind, currentLeaderboardId;
end

--SubKind1: 領取排名獎勵 +結果[0:成功 1:失敗 2:活動未結束 3:太頻繁 4:未上榜 5:背包滿 255:功能未開放]
function UILeaderboardSimple.ReceiveRequst(subKind, result)
  if subKind == 1 then
    if result == 0 then
      image_Reward.sprite = UI.GetSprite("but001L");
      text_Claim.text = string.Get(98017);
    elseif result == 1 then
      ShowCenterMessage(string.Concat(string.Get(99689),string.Get(80012))); --領取失敗
    elseif result == 2 then
      ShowCenterMessage(string.Get(24380)); --尚未結算
    elseif result == 3 then
      ShowCenterMessage(string.Get(22841)); --請稍後再點擊
    elseif result == 4 then
      ShowCenterMessage(string.Get(20595)); --尚未上榜
    elseif result == 5 then
      ShowCenterMessage(string.Get(30038)); --抱歉，你的背包已經滿了！
    else
      ShowCenterMessage(string.Get(80332)); --功能尚未開放
    end
  end
end

function UILeaderboardSimple.UpdateBoard()
  local resultKind, resultBoardId = UILeaderboardSimple.GetCurrentOpenBoard();
  UILeaderboardSimple.SendRequest(resultKind, resultBoardId);
end

function UILeaderboardSimple.UpdateUI(leaderboardId, page, maxPage, myRank, myScore, rankPlayers)
  currentLeaderboardId = leaderboardId;
  currentPage = math.max(page, 1);
  totalPage = math.max(maxPage, 1);
  datas = rankPlayers or {};

  text_Page.text = string.Concat(currentPage, " / ", totalPage);

  if myRank > 0 then
    text_Ranking.text = myRank;
  else
    text_Ranking.text = "---";
  end

  if myScore then
    text_Score.text = string.AddBrackets(string.Concat(string.Get(20598), " : ", tostring(myScore)));
  else
    text_Score.text = string.AddBrackets(string.Concat(string.Get(20598), " : ", "---"));
  end

  text_Empty.gameObject:SetActive(table.maxn(datas) <= 0);
  this.SetCalcMask();
  scrollContent_LeaderboardSimple:Reset(math.min(table.maxn(datas), maxInfo));
  this.UpdatePage();
  this.CheckActivityEnd();
end

function UILeaderboardSimple.Onclick_ClaimReward()
  if currentBoardKind == EBoardKind.ActivityModel then
    local status, statusString = ActivityModel.GetActivityStatus(ActivityModel.openActivityId);
    if status == EFestivalStatus.ActivityEnd then
      if not ActivityModel.PlayerAwardCount[currentLeaderboardId * ActivityModel.MISSION_INDEX + 1] or ActivityModel.PlayerAwardCount[currentLeaderboardId * ActivityModel.MISSION_INDEX + 1].get == 0 then
        LeaderboardSimpleData.GetRankReward(currentBoardKind, currentLeaderboardId);
      else
        image_Reward.sprite = UI.GetSprite("but001L");
        text_Claim.text = string.Get(98017); --已領取
        ShowCenterMessage(string.Get(98017)); -- 已領取
      end
    else
      return;
    end
  end
end

function UILeaderboardSimple.OnClick_PrevPage(event)
  if currentPage == 1 then return end;

  currentPage = currentPage - 1;
  this.UpdatePage();
end

function UILeaderboardSimple.OnClick_NextPage(event)
  if currentPage == totalPage then return end;

  currentPage = currentPage + 1;
  this.UpdatePage();
end

function UILeaderboardSimple.OnClick_Refresh()
  clickResult, lastClickRefreshTime = this.CheckClickTime(lastClickRefreshTime);
  if not clickResult then return end;
  this.SendRequest(currentBoardKind, currentLeaderboardId);
end

function UILeaderboardSimple.OnInitialize_scrollContent_LeaderboardSimple(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_LeaderboardSimple[itemIndex] = {};
    scrollItems_LeaderboardSimple[itemIndex].itemChip = {};
    scrollItems_LeaderboardSimple[itemIndex].image_Rank = scrollItems[itemIndex]:Find("Image_Rank"):GetComponent("Image");
    scrollItems_LeaderboardSimple[itemIndex].text_Rank = scrollItems[itemIndex]:Find("Text_Rank"):GetComponent("Text");
    scrollItems_LeaderboardSimple[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_LeaderboardSimple[itemIndex].text_Score = scrollItems[itemIndex]:Find("Text_Score"):GetComponent("Text");
    scrollItems_LeaderboardSimple[itemIndex].text_Title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");

    for i = 1, 5 do
      scrollItems_LeaderboardSimple[itemIndex].itemChip[i] = {};
      scrollItems_LeaderboardSimple[itemIndex].itemChip[i].gameObject = scrollItems[itemIndex]:Find("Chips"):Find(string.Concat("Image_ItemChip_", i)).gameObject;
      scrollItems_LeaderboardSimple[itemIndex].itemChip[i].item = UIItemController.New(scrollItems_LeaderboardSimple[itemIndex].itemChip[i].gameObject.transform);
      scrollItems_LeaderboardSimple[itemIndex].itemChip[i].text_Quant = scrollItems_LeaderboardSimple[itemIndex].itemChip[i].gameObject.transform:Find("Text_Quant"):GetComponent("Text");
    end
  end
end

function UILeaderboardSimple.OnItemChange_scrollContent_LeaderboardSimple(dataIndex, itemIndex)
  board = LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId);
  if table.Count(board) <= 0 then return true end
  local rankPlayers = board.players[dataIndex + 1];
  if rankPlayers == nil then
		return false ;
  else
    text_Empty.gameObject:SetActive(table.Count(board.players) <= 0);
	end


  if dataIndex + 1 <= 3 then
    scrollItems_LeaderboardSimple[itemIndex].image_Rank.sprite = UI.GetRankSprite(dataIndex + 1);
    scrollItems_LeaderboardSimple[itemIndex].image_Rank.gameObject:SetActive(true);
    scrollItems_LeaderboardSimple[itemIndex].text_Rank.text = "";

  else
    scrollItems_LeaderboardSimple[itemIndex].image_Rank.sprite = nil;
    scrollItems_LeaderboardSimple[itemIndex].image_Rank.gameObject:SetActive(false);
    scrollItems_LeaderboardSimple[itemIndex].text_Rank.text = dataIndex + 1;
  end

  this.UpdateChips(currentBoardKind, itemIndex, dataIndex + 1);

  scrollItems_LeaderboardSimple[itemIndex].text_Name.text = rankPlayers.playerName;
  scrollItems_LeaderboardSimple[itemIndex].text_Score.text = rankPlayers.playerScore;
  scrollItems_LeaderboardSimple[itemIndex].text_Title.text = "";

  return true;
end

function UILeaderboardSimple.UpdatePage()
  if table.Count(LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId)) <= 0 then return true end
  for i = 1, this.pageRawAmount do
    local playerIndex = i + this.pageRawAmount * (currentPage - 1);
    local player = LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId).players[playerIndex];
    scrollItems_LeaderboardSimple[i - 1].image_Rank.sprite = nil;
    scrollItems_LeaderboardSimple[i - 1].image_Rank.gameObject:SetActive(false);
    scrollItems_LeaderboardSimple[i - 1].text_Rank.text = "";
    scrollItems_LeaderboardSimple[i - 1].text_Name.text = "";
    scrollItems_LeaderboardSimple[i - 1].text_Score.text = "";

		if player then
      if i + this.pageRawAmount * (currentPage - 1) <= 3 then
        scrollItems_LeaderboardSimple[i - 1].image_Rank.sprite = UI.GetRankSprite(playerIndex);
        scrollItems_LeaderboardSimple[i - 1].image_Rank.gameObject:SetActive(true);
      else
        scrollItems_LeaderboardSimple[i - 1].image_Rank.sprite = nil;
        scrollItems_LeaderboardSimple[i - 1].image_Rank.gameObject:SetActive(false);
      end
      scrollItems_LeaderboardSimple[i - 1].text_Rank.text = playerIndex;
      scrollItems_LeaderboardSimple[i - 1].text_Name.text = player.playerName;
      scrollItems_LeaderboardSimple[i - 1].text_Score.text = player.playerScore;
    else
      scrollItems_LeaderboardSimple[i - 1].text_Rank.text = "";
      scrollItems_LeaderboardSimple[i - 1].text_Name.text = "";
      scrollItems_LeaderboardSimple[i - 1].text_Score.text = "";
    end
    this.UpdateChips(currentBoardKind, i - 1, playerIndex);
  end
	text_Page.text = string.Concat(currentPage, " / ", totalPage);
end

function UILeaderboardSimple.CheckActivityEnd()
  if currentBoardKind == EBoardKind.ActivityModel then
    local status, statusString = ActivityModel.GetActivityStatus(ActivityModel.openActivityId);
    if status == EFestivalStatus.ActivityEnd and board.status == EBoardStatus.Reward then
      if not ActivityModel.PlayerAwardCount[currentLeaderboardId * ActivityModel.MISSION_INDEX + 1] or ActivityModel.PlayerAwardCount[currentLeaderboardId * ActivityModel.MISSION_INDEX + 1].get == 0 then
        if LeaderboardSimpleData.GetRank(currentBoardKind, currentLeaderboardId) ~= 0 then
          image_Reward.sprite = UI.GetSprite("but001D");
          text_Claim.text = string.GetColorText(string.Get(21062), Color.Yellow); --可領取
        end
      elseif ActivityModel.PlayerAwardCount[currentLeaderboardId * ActivityModel.MISSION_INDEX + 1].get == 1 then
        image_Reward.sprite = UI.GetSprite("but001L");
        text_Claim.text = string.Get(98017); --已領取
      end
    else
      image_Reward.sprite = UI.GetSprite("but001L");
    end
  end
end

function UILeaderboardSimple.CheckClickTime(lastClickTime)
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

function UILeaderboardSimple.UpdateChips(boardKind, itemIndex, index)
  if table.Count(LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId)) <= 0 then return true end
  if boardKind == EBoardKind.ActivityModel then
    local mission = ActivityModel.ActivityData[ActivityModel.openActivityId].page[2].missions;
    local player = LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId).players[index];
    local isClaimed = false;

		mission = ActivityModel.SortMission(mission);
    for i = 1, 5 do
      scrollItems_LeaderboardSimple[itemIndex].itemChip[i].gameObject:SetActive(false);
    end

    for i, v in ipairs(mission)do
      local isUpdate = false;
      if v.operator == EMissionOperator.Equal and v.order == index and player then
        isUpdate = true;
        isClaimed = true;
      elseif v.operator == EMissionOperator.SmallEqual and v.conditionCount >= index and player and not isClaimed then
        isUpdate = true;
        isClaimed = true;
      end

      if isUpdate then
        for i = 1, 5 do --檢查任務表的五個獎勵
          if v.itemChange[i].itemId ~= nil and v.itemChange[i].itemId ~= 0 and v.itemChange[i].kind > 0 and v.itemChange[i].kind < 5 then
            -- 找到空的 chip 並填充
            for j = 1, 5 do
              if not scrollItems_LeaderboardSimple[itemIndex].itemChip[j].gameObject.activeSelf then
                scrollItems_LeaderboardSimple[itemIndex].itemChip[j].item:SetItem(v.itemChange[i].itemId, v.itemChange[i].quant);
                scrollItems_LeaderboardSimple[itemIndex].itemChip[j].gameObject:SetActive(true);
                break;
              end
            end
          end
        end
      end
    end
  end
end

function UILeaderboardSimple.SetScore(score)
  text_Score.text = tostring(score);
end

function UILeaderboardSimple.SetCalcMask()
  board = LeaderboardSimpleData.GetSelfBoard(currentBoardKind, currentLeaderboardId);
  local status, statusString = ActivityModel.GetActivityStatus(ActivityModel.openActivityId);
  image_CalcScoreMask.gameObject:SetActive(false);
  if not text_Empty.gameObject.activeSelf and board.status ~= EBoardStatus.Reward  then
    if status == EFestivalStatus.ActivityEnd and not image_CalcScoreMask.gameObject.activeSelf then
      image_CalcScoreMask.gameObject:SetActive(true);
    end
  end
end