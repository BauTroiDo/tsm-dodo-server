EventHandler = {}

EventHandler[0] = function(eventInfo, eventManager, surfaceDatas)
  local role = Role.mapNpcs[eventInfo.parameter];
  
  eventManager.SetConduct(true);   --用來控制效果循序執行

  if eventInfo.resultClass == 1 then
    --道具
    ShowCenterMessage(string.Concat(itemDatas[eventInfo.parameter]:GetName(true), tostring(eventInfo.resultValue)));
  elseif eventInfo.resultClass == 2 then
    --任務/標記
    if eventInfo.parameterStyle == 1 then
      --增加標記
      if (MaxMissionQuant - MarkManager.missions[eventInfo.parameter].step) >= eventInfo.resultValue then
        MarkManager.missions[eventInfo.parameter].step = MarkManager.missions[eventInfo.parameter].step + eventInfo.resultValue;
        logWarn(string.Concat("標記", tostring(eventInfo.parameter), " ", markDatas[eventInfo.parameter].name, "+", tostring(eventInfo.resultValue), " = ", tostring(MarkManager.missions[eventInfo.parameter].step), "個標記"));
      end
    elseif eventInfo.parameterStyle == 2 then
      --刪除標記
      if MarkManager.missions[eventInfo.parameter].step >= eventInfo.resultValue then
        MarkManager.missions[eventInfo.parameter].step = MarkManager.missions[eventInfo.parameter].step - resultValue;
        logWarn(string.Concat("標記", tostring(eventInfo.parameter), " ", markDatas[eventInfo.parameter].name, "-", tostring(eventInfo.resultValue), " = ", tostring(MarkManager.missions[eventInfo.parameter].step), "個標記"));
      end
    end
  elseif eventInfo.resultClass == 3 then
    --NPC
    if eventInfo.parameterStyle == 1 then
      --要求加入玩家
      if table.Count(followNpcs) < Role.maxFollowNpc then
        local inTeam = false;

        local followNpcs = Role.GetFollowNpcs(Role.playerId);
        for k, v in pairs(followNpcs) do
          if v.npcId == role.npcId then
            inTeam = true;
            break;
          end
        end
        
        if not inTeam then
          --[[
          CY_FNpcTeamMenu.FocusIndex:=Focus.Index;
          CY_FNpcTeamMenu.btnJoinTeam.appear;
          CY_FNpcTeamMenu.btnDisJoinTeam.DisAppear;
          CY_FNpcTeamMenu.btnGoFight.DisAppear;        
          CY_FNpcTeamMenu.editLabel.Text:='是否讓'+MapNpc[Focus.Index].Name+'加入?';
          CY_FNpcTeamMenu.Appear;
          CY_FNpcTeamMenu.Bool:=True;
          ]]--
        end
      end
    elseif eventInfo.parameterStyle == 2 then
      --追擊玩家
      --[[ 等龍哥弄好
      PursueFlag = true;
      PursueIDx = parameter;
      MapNpc[parameter].PathMedIndex = 2;
      MapNpc[parameter].Speed = Player.Speed*(0.5)*NpcArray[parameter].TraceSpeedLevel;
      ]]--
    elseif eventInfo.parameterStyle == 3 then
      --隱形
      role:SetVisible(ERoleVisible.TimeHide, eventInfo.resultValue);
    elseif eventInfo.parameterStyle == 4 then
      --顯現
      role:SetVisible(ERoleVisible.TimeVisible, eventInfo.resultValue);
    elseif eventInfo.parameterStyle == 5 then
      --更換圖序
      ShowCenterMessage(string.Get(40324), 1);
    end
  elseif eventInfo.resultClass == 8 then
    --[[ 不知道是三小
    if eventInfo.parameterStyle == 1 then
      MessageBox(DZ_Game.Form1.Handle, PChar(String("^__^"+NPCData.Who[NPCData.FindOne(parameter)].Name+" 加入")), "黑暗的勢力又增加了...", MB_ICONASTERISK);
    elseif eventInfo.parameterStyle == 2 then
      MessageBox(DZ_Game.Form1.Handle, PChar(String("=__="+NPCData.Who[NPCData.FindOne(parameter)].Name+" 離開")), "黑暗的勢力削弱了...", MB_ICONASTERISK);
    end
    ]]--
  end  
end

EventHandler[1] = function(eventInfo, eventManager, surfaceDatas)
  local role = Role.mapNpcs[eventInfo.parameter];

  if Define.IsDebugMode() then    
    if eventInfo == nil then
      Chat.AddMessage(EChannel.System, "ErrorMessage: talk eventInfo nil");
      return;
    end;
    if eventInfo.resultMeanNo == nil then
      Chat.AddMessage(EChannel.System, "ErrorMessage: talk resultNo nil");
      return;
    end;
    if talkDatas == nil then
      Chat.AddMessage(EChannel.System, "ErrorMessage: talk data nil");
      return;
    end
    if talkDatas[eventInfo.resultMeanNo] == nil then
      Chat.AddMessage(EChannel.System, string.format("ErrorMessage: talk data No: %s nil", tostring(eventInfo.resultMeanNo)));    
      return;
    end      
  end

  eventManager.SetConduct(false);   --事件處理暫停,此行專用於Click的循序處理方式

  --面對面講話
  if role ~= nil then
    Role.player:FaceTo(role.position);

    if not role.isMoving and (npcDatas[role.npcId] == nil or not Contains(npcDatas[role.npcId].kind, ENpc.Billboard)) then
      role:FaceTo(Role.player.position, true);
    end
  end

  local showRole = nil;
  if eventInfo.resultClass == 3 then
    --NPC
    showRole = role;
  elseif eventInfo.resultClass == 7 then
    --玩家
    showRole = Role.player;
  elseif resultClass == 12 then
    --二代武將
    showRole = Role.childNpc;
  end    

  ShowTalkMessage(
    function()
      if role ~= nil and not role.isMoving then
        role:SetDirection(role.data.eventNpcData.direction);
      end

      if eventManager.IsRunning then
        eventManager.SetConduct(true);
      end
      --runevent
    end,
    talkDatas[eventInfo.resultMeanNo],
    showRole
  );
end

EventHandler[2] = function(eventInfo, eventManager, surfaceDatas)
  eventManager.SetConduct(true); --用來控制效果循序執行

  --[[
  C_SendDoorMsg.DoorNo := DoorMeetNo; //觸發的door編號
  C_SendDoorMsg.EveNo := NowRunningEve;

  C_RevDoorInfoMsg.DoorNo := DoorMeetNo;  //目前暫用Client這邊的data
  C_RevDoorInfoMsg.EveNo := NowRunningEve;  //目前暫用Client這邊的data
  {$ifDef _IsSimServer}
    C_RevDoorInfoFlag := True;
    BKSimServer.ReceiveDoorInfo(C_SendDoorMsg); //Server_Rev //值送給SimServer
  {$Else}
  {$EndIf}
  ]]--
end

EventHandler[3] = function(eventInfo, eventManager, surfaceDatas)
  eventManager.SetSession(true);
  --[[
  FightMsg := FightArray[resultMeanNo];  //戰鬥資料應該用Server端的

  C_SSessioning := True;
  FConnect.SedMessage(11, 1);
  ]]--
end

EventHandler[4] = function(eventInfo, eventManager, surfaceDatas)
  local role = Role.mapNpcs[eventInfo.parameter];

  eventManager.SetConduct(true); --用來控制效果循序執行

  if eventInfo.resultClass == 3 then
    --NPC
    role:Move(Vector2.New((eventInfo.resultValue / 10000) * 20 - 10, (eventInfo.resultValue % 10000) * 20));
  end
end

EventHandler[5] = function(eventInfo, eventManager, surfaceDatas)
  if eventInfo.parameterStyle == 1 then
    eventManager.SetConduct(false); --直到撥放結束
  elseif eventInfo.parameterStyle == 2 then
    eventManager.SetConduct(false); --同步效果執行(只可與放映或換場效果合用)
  end
  --eventManager.SetConduct(true);  
  MoviePlayer.PlayMovie(eventInfo.resultValue);  
end

EventHandler[6] = function(eventInfo, eventManager, surfaceDatas)
  local role = Role.mapNpcs[eventInfo.parameter];

  if eventInfo.parameterStyle == 0 then
    --無屬性(玩家自己)
    eventManager.SetConduct(true);          --用來控制效果循序執行
    eventManager.SetInteracting(true);      --設定目前處於互動效果中
  elseif eventInfo.parameterStyle == 1 then
    --隊長
    eventManager.SetConduct(false);         --用來控制效果循序執行
    eventManager.SetInteracting(true);      --設定目前處於互動效果中
  elseif eventInfo.parameterStyle == 2 then
    --隊員(First)
  end

  --面對面講話
  if role ~= nil then
    Role.player:FaceTo(role.position);

    if not role.isMoving and (npcDatas[role.npcId] == nil or not Contains(npcDatas[role.npcId].kind, ENpc.Billboard)) then
      role:FaceTo(Role.player.position, true);
    end
  end
  
  --互動資料用Server端的resultMeanNo取得
  local surfaceData = surfaceDatas[eventInfo.resultMeanNo];

  --事件觸發選擇
  if surfaceData.optionMode == 0 then

  elseif surfaceData.optionMode == 1 then
    --NPC交易用
    --[[
    BK_InterFace.ResetInterForm;

    -- 只有在按別的交易介面或是換場景,時才消除ListBox的記憶
    if BK_InterFace.OldMap ~= Player.SceneNum or BK_InterFace.OldSurface ~= surfaceData.SurfaceNO then
      BK_InterFace.LB1ScrollBar1.currentRec = 0;
      BK_InterFace.ListBox1.TopLineOnList = 0;
      BK_InterFace.OldSurface = surfaceData.SurfaceNO;
      BK_InterFace.OldMap = Player.SceneNum;
    end

    BK_InterFace.ListBox1.OnLeftSelect = BK_InterFace.SelThingItem;
    --要放小圖片所以加大了
    BK_InterFace.ListBox1.ItemHeight = BuyFormItemHigh;
    BK_InterFace.ListBox1.ItemLeft = 32;
    BK_InterFace.ListBox1.ImgHide = False;
    ]]--

  elseif surfaceData.optionMode == 2 then

  elseif surfaceData.optionMode == 3 then
  
  end

  local buttonCount = 0;
  local message = "";
  local options = {};
  local tempString = "";
  local seOrder = { 0, 0, 0 }

  logWarn(string.Concat("Option Mode : ", surfaceData.optionMode));

  --計算高度與設定文字內容
  for i = 1, surfaceData.sentenceCount do
    if surfaceData.sentences[i] ~= nil then
      --找出Surface中se/bk物件排列順位
      if surfaceData.sentences[i].style ~= 4 then
        for j = 1, 3 do
          if seOrder[j] == 0 then
            seOrder[j] = surfaceData.sentences[i].style;
            break;
          elseif seOrder[j] == surfaceData.sentences[i].style then
            break;
          end
        end
      end

      if surfaceData.sentences[i].style == 1 then
        --秀文字
        if surfaceData.sentences[i].dataKind == 1 then
          tempString = string.Concat(tempString, talkDatas[surfaceData.sentences[i].dataId]);
        elseif surfaceData.sentences[i].dataKind == 2 then
          tempString = string.Concat(tempString, itemDatas[surfaceData.sentences[i].dataId]:GetName(true), " $", tostring(itemDatas[surfaceData.sentences[i].dataId].price));
        end

        --累積 tempString
        if surfaceData.sentences[i].canCut then

          --[[
          if surfaceData.optionMode == 1 then
            BK_InterFace.TextMemoAndOther = 15;
            BK_InterFace.TextMemoHeight = BK_InterFace.TextMemoHeight + (20 * math.ceil((string.len(tempString) * (16 / 2)) / BK_InterFace.InterTextMemoWidthLimit));
          end
          ]]--

          message = string.Concat(message, tempString); --sega
        end

      elseif surfaceData.sentences[i].style == 2 then
        --按鈕
        if surfaceData.sentences[i].dataKind == 1 then
          tempString = talkDatas[surfaceData.sentences[i].dataId];
        elseif surfaceData.sentences[i].dataKind == 2 then
          tempString = string.Concat(itemDatas[surfaceData.sentences[i].dataId]:GetName(true), " $", tostring(itemDatas[surfaceData.sentences[i].dataId].price));
        end

        --[[
        if surfaceData.optionMode == 1 then
          BK_InterFace.BtnAndOther = 15;
          -- 如果有換行 或者為 Btn[0]
          if surfaceData.sentences[i].canCut or buttonCount == 0 then
            BK_InterFace.BtnTeamHeight = BK_InterFace.BtnTeamHeight + (BK_InterFace.BtnArray[buttonCount].Height+BK_InterFace.BtnInterval);
          end

          --減去最後一排按鈕的間隔,且只要做一次就好了
          if buttonCount == 0 then
            BK_InterFace.BtnTeamHeight = BK_InterFace.BtnTeamHeight - BK_InterFace.BtnInterval;
          end
          
          --每個字的距離
          BK_InterFace.BtnArray[buttonCount].Width = Length(tempString)*17;
          BK_InterFace.BtnArray[buttonCount].Caption = tempString;

          -- 紀錄每個按鈕要不要換行
          BK_InterFace.BtnInfoArray[buttonCount] = surfaceData.sentences[i].canCut;
        end

        buttonCount = buttonCount + 1;
        ]]--

        --table.insert(options, tempString);
        
      elseif surfaceData.sentences[i].style == 3 then
        --選單
        --[[
        --戰役選單特別處理
        if SceneManager.sceneId == 12711 and MapNpc[eventInfo.parameter].ID == 16037 then
          if EventManage.WeekNum == 1 then
            --第一個星期不開放鉅鹿、虎牢關之戰選項
            if Contains(surfaceData.sentences[i].dataId, 28336, 29734) then
              Continue;
            end

          elseif EventManage.WeekNum == 2 then
            --第二個星期不開放大興山、虎牢關之戰選項
            if Contains(surfaceData.sentences[i].dataId, 28267, 29734) then
              Continue;
            end

          elseif EventManage.WeekNum == 3 then
            --第三個星期不開放大興山、鉅鹿之戰選項
            if Contains(surfaceData.sentences[i].dataId, 28267, 28336) then
              Continue;
            end
          end
        end
        ]]--

        if surfaceData.sentences[i].dataKind == 1 then
          tempString = string.Concat(tempString, talkDatas[surfaceData.sentences[i].dataId]);
        elseif surfaceData.sentences[i].dataKind == 2 then
          if surfaceData.optionMode == 0 then
            tempString = string.Concat(itemDatas[surfaceData.sentences[i].dataId]:GetName(true), " $", tostring(itemDatas[surfaceData.sentences[i].dataId].price), "      $", tostring(itemDatas[surfaceData.sentences[i].dataId].level));
            GoodsPic = "";
          
            --[[
            if Define.Thai then
              if surfaceData.optionMode == 1 then
                BK_InterFace.ListBoxAndOther = 15;
                BK_InterFace.ListBox1.Add(surfaceData.sentences[i].dataId);
              end
            end
            ]]--
          elseif surfaceData.optionMode == 1 then
            if Define.Thai then
              --[[
              if surfaceData.optionMode == 1 then
                BK_InterFace.ListBoxAndOther = 15;
                BK_InterFace.ListBox1.Add(surfaceData.sentences[i].dataId);
              end
              ]]--
            else
              --物品效果  Max = 17個AsSCII code
              tempString = Item.GetItemEffectString(surfaceData.sentences[i].dataId);
            end
          end
        end

        --[[
        if not Define.Thai then
          if surfaceData.optionMode == 1 then
            BK_InterFace.ListBoxAndOther = 15;
            BK_InterFace.ListBox1.Line1.Add(Line1);
            BK_InterFace.ListBox1.Add(tempString, GoodsPic);
          end
        end
        ]]--

        table.insert(options, tempString);

      elseif surfaceData.sentences[i].style == 4 then
        --特殊關閉按鈕
        if surfaceData.sentences[i].dataKind == 1 then
          tempString = talkDatas[surfaceData.sentences[i].dataId];
        elseif surfaceData.sentences[i].dataKind == 2 then
          tempString = string.Concat(itemDatas[surfaceData.sentences[i].dataId]:GetName(true), " $", tostring(itemDatas[surfaceData.sentences[i].dataId].price));
        end

        --[[
        if surfaceData.optionMode == 1 then
          BK_InterFace.Cl_Btn.Appear;
        end
        ]]--
      end
          
      logWarn(string.Concat("Sentence[", i, "] Style : ", surfaceData.sentences[i].style, " Text : ", tempString));
      tempString = ""; --清空
    end
  end

  if surfaceData.optionMode == 0 then
    local showRole = nil;

    if eventInfo.parameter == 0 then  
      showRole = Role.player;
    else
      showRole = role;
    end

    if table.maxn(options) > 0 then
      ShowOptionMessage(
        function(result)
          if role ~= nil and not role.isMoving then
            role:SetDirection(role.data.eventNpcData.direction);
          end
          
          if result == 0 then
            eventManager.SelectEvent(40);
          elseif result <= 10 then
            result = result - 1;
            eventManager.SelectEvent(tonumber(string.Concat("3", tostring(result))));
          else
            result = result - 1;
            eventManager.SelectEvent(tonumber(string.Concat("6", tostring(result - 9))));
          end
        end,
        message,
        options,
        showRole
      );
    else
      ShowCheckMessage(
        function(result)
          if role ~= nil and not role.isMoving then
            role:SetDirection(role.data.eventNpcData.direction);
          end

          if result then
            eventManager.SelectEvent(20);
          else
            eventManager.SelectEvent(21);
          end     
        end,
        message,
        showRole
      );
    end

  elseif surfaceData.optionMode == 1 then
    UI.Open(UIShop, EUIShop.Shop, options);
    --[[
    --配置物件位置
    for i = 1, 3 do
      if seOrder[i] == 1 then
        --Memo
      elseif seOrder[i] == 2 then
        --Button
      elseif seOrder[i] == 3 then
        --ListBox
        BK_InterFace.ListBox1.Appear;

        --如果配置的第一位是ListBox
        if i = 1 then
          BK_InterFace.ListBox1.SetBound(19, 60, 405, BuyFormItemHigh*BuyFormItemNum);
        else
          BK_InterFace.ListBox1.SetBound(19, 60, 405, BuyFormItemHigh*BuyFormItemNum);
        end

        InterFormMaxHeight = InterFormMaxHeight + BK_InterFace.ListBox1Height + BK_InterFace.ListBoxAndOther;

        --設定ScrollBar
        with BK_InterFace.LB1ScrollBar1 do
        begin
          Left = BK_InterFace.ListBox1.Left+BK_InterFace.ListBox1.Width;
          Top = BK_InterFace.ListBox1.Top-1;
          Height = BK_InterFace.ListBox1.Height;
        end

        BK_InterFace.ListBox1.setControler(BK_InterFace.LB1ScrollBar1);
        BK_InterFace.LB1ScrollBar1.appear;
      end
    end

    BK_InterFace.appear;
    ]]--

  elseif surfaceData.optionMode == 2 then
    --[[ 舊版軍團製造
    local npcs = {};

    for i = 1, surfaceData.sentenceCount do
      if surfaceData.sentences[i].style == 1 then
        message = talkDatas[surfaceData.sentences[i].dataId];
      elseif surfaceData.sentences[i].style == 3 then
        if surfaceData.sentences[i].dataKind == 3 then
          table.insert(npcs, surfaceData.sentences[i].dataId);
        end
      end
    end

    UI.Open(UIArmyMake,
      message,
      npcs,
      function(result)
        UI.Close(UIArmyMake);

        if role ~= nil and not role.isMoving then
          role:SetDirection(role.data.eventNpcData.direction);
        end

        if result == 0 then
          eventManager.SelectEvent(40);
        elseif result <= 9 then
          result = result - 1;
          eventManager.SelectEvent(tonumber(string.Concat("3", tostring(result))));
        else
          result = result - 1;
          eventManager.SelectEvent(tonumber(string.Concat("6", tostring(result - 9))));
        end
      end
    );
    --]]
  elseif surfaceData.optionMode == 3 then
  end
end
