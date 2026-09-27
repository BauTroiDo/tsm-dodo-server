UIArmyBattle = {}
local this = UIArmyBattle;

this.name = "UIArmyBattle";
this.uiController = nil;
this.HPLENGTH = 8;

local MAPHEIGHT = 1280;
local MAPWIDTH = 1792;
local HPHEIGHT = 15;
local TOTAL_CITY_COUNT = 28;

local functionRoot;
local cityRoot;
local lineRoot;
local gameObject_MultiCity;
local gameObject_Plus;
local text_Status;
local text_Defence;
local text_AttackCount;
local text_AttackCountTime;
local text_PlayerScore;
local text_ActivityTime;

local functionObjs = {};
local cityItems = {};
local armyInfo = {};

local panelStartPosition = Vector2.zero;
local moveDirection = Vector2.zero;
local minTouchMoveValue = 45.0;
local fireId = 50269001;
local hasDragged = false;

local multiPos;
local screenW;
local screenH;
local orderCount = 5;

local mapImages = {}
local bgRootTransform;
local bgImageNode;

function UIArmyBattle.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  functionRoot = uiController:FindGameObject("Function_Bottom").transform;
  cityRoot = uiController:FindGameObject("CityRoot").transform;
  bgRootTransform = uiController:FindGameObject("BGRoot").transform;
  bgImageNode = uiController:FindGameObject("Image_");
  gameObject_MultiCity = uiController:FindRawImage("RawImage_BG");
  gameObject_Plus = uiController:FindEvent("Image_Plus").gameObject;

  text_Status = uiController:FindText("Text_Status");
  text_Defence = uiController:FindText("Text_Defence");
  text_AttackCount = uiController:FindText("Text_AttackCount");
  text_AttackCountTime = uiController:FindText("Text_AttackCountTime");
  text_PlayerScore = uiController:FindText("Text_PlayerScore");
  text_ActivityTime = uiController:FindText("Text_ActivityTime");

  local tempEvent = uiController:FindEvent("Image_Help");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Help);

  tempEvent = uiController:FindEvent("Panel_Drag");
  tempEvent:SetListener(EventTriggerType.BeginDrag, this.OnPress_Panel);
  tempEvent:SetListener(EventTriggerType.Drag, this.OnDrag_Panel);
  --tempEvent:SetListener(EventTriggerType.EndDrag, this.OnRelease_Panel);

  tempEvent = gameObject_Plus:GetComponent("UIEvent");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Plus);

  local cityObj = cityRoot:Find(string.Concat("City")).gameObject;
  TOTAL_CITY_COUNT = table.Count(strongholdDatas);

  for i = 1, TOTAL_CITY_COUNT do
    local cityTemp = newObject(cityObj);
    cityTemp.transform:SetParent(cityRoot);
    cityTemp:GetComponent("RectTransform").localScale = Vector2.New(1, 1);

    cityItems[i] = {};
    cityItems[i].gameObject = cityTemp;
    cityItems[i].transform = cityItems[i].gameObject.transform;
    cityItems[i].rectTransform = cityItems[i].transform:GetComponent("RectTransform");
    cityItems[i].city = cityItems[i].transform:Find("Image_City"):GetComponent("Image");
    cityItems[i].cityRect = cityItems[i].city:GetComponent("RectTransform");
    cityItems[i].image_fight = cityItems[i].city.transform:Find("RawImage_Fight"):GetComponent("UIRawImage");
    TextureManager.SetPng(ETextureUseType.None, "Attack", cityItems[i].image_fight);
    cityItems[i].image_fight:ShiftUV(1, 5, 0.1, -1);
    cityItems[i].cityFire = {};
    for j = 1, 2 do
      cityItems[i].cityFire[j] = cityItems[i].city.transform:Find(string.Concat("RawImage_Fire (", j ,")")):GetComponent("UIRawImageEx");
    end
    cityItems[i].hpSlider = cityItems[i].transform:Find("Slider_Hp"):GetComponent("UISlider");
    cityItems[i].sliderRect = cityItems[i].hpSlider:GetComponent("RectTransform");
    cityItems[i].sliderColor = cityItems[i].hpSlider.transform:Find("Fill Area"):Find("Fill"):GetComponent("Image");
    cityItems[i].flag = cityItems[i].hpSlider.transform:Find("Image_Flag"):GetComponent("Image");
    cityItems[i].text_defence =cityItems[i].hpSlider.transform:Find("Text_Defence"):GetComponent("Text");
    cityItems[i].text_Index =cityItems[i].flag.transform:Find("Text_Life"):GetComponent("Text");
    cityItems[i].text_status =cityItems[i].hpSlider.transform:Find("Text_CityStatus"):GetComponent("Text");
    cityItems[i].button = cityItems[i].transform:Find("Image_Button").gameObject;
    cityItems[i].buttonRect = cityItems[i].button:GetComponent("RectTransform");
    cityItems[i].text_Button = cityItems[i].transform:Find("Image_Button"):Find("Text_Submit"):GetComponent("Text");
    cityItems[i].timerEvent = nil;
    cityItems[i].lineInfo = {};
    cityItems[i].lineInfo.lines = {};
    cityItems[i].lineInfo.index = {};

    tempEvent = cityItems[i].city.gameObject:GetComponent("UIEventDrag");
    tempEvent:SetListener(EventTriggerType.BeginDrag, this.OnPress_Panel);
    tempEvent:SetListener(EventTriggerType.Drag, this.OnDrag_Panel);
    tempEvent = cityItems[i].city.gameObject:GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerDown, function() hasDragged = false; end);
    tempEvent:SetListener(EventTriggerType.PointerUp, function() this.OnClick_City(i); end);


    tempEvent = cityItems[i].button:GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, function() this.OnClick_Button(i); end);
  end

  destroy(cityObj);

  for i = 1, 2 do
     local root;
    if i == ArmyMoralColor.Red then
      root = uiController:FindGameObject("RedInfo").transform;
    elseif i == ArmyMoralColor.Blue then
      root = uiController:FindGameObject("BlueInfo").transform;
    end

    armyInfo[i] = {};
    armyInfo[i].infoEnsign = root:Find("Image_InfoEnsignBG"):Find("RawImage_InfoEnsign"):GetComponent("RawImage")
    armyInfo[i].ensignTexture = TexturePool.Get(armyInfo[i].ensignTexture, 24, 24, TextureFormat.RGBA4444);
    armyInfo[i].name = root:Find("Text_Name"):GetComponent("Text");
    armyInfo[i].count = root:Find("Text_Score"):GetComponent("Text");
    local temp = root:Find("OrderRoot");
    armyInfo[i].orders = {};

    for j = 1, orderCount do
      armyInfo[i].orders[j] = {};
      armyInfo[i].orders[j].gameObject = temp:Find(string.Concat("Image_Order (",j ,")")).gameObject;
      armyInfo[i].orders[j].text_Index = armyInfo[i].orders[j].gameObject.transform:Find("Text_Index"):GetComponent("Text");
    end
  end

  lineRoot = uiController:FindGameObject("LineRoot").transform;

  this.InitializePage();
  this.InitializeUIBackground();
end

function UIArmyBattle.InitializeUIBackground()
  local rows = 5
  local columns = 7
  local imageSize = 256
  local totalWidth = imageSize * columns
  local totalHeight = imageSize * rows

  local startX = -totalWidth / 2 + imageSize / 2
  local startY = totalHeight / 2 - imageSize / 2

  local oddRowSprites = {"common0_bod166", "common0_bod165", "common0_bod164", "common0_bod164", "common0_bod165", "common0_bod164", "common0_bod165"} --基數貼圖排列順序
  local evenRowSprites = {"common0_bod165", "common0_bod164", "common0_bod164", "common0_bod165", "common0_bod164", "common0_bod165", "common0_bod166"} --偶數貼圖排列順序

  for row = 1, rows do
    local spriteRow = (row % 2 == 1) and oddRowSprites or evenRowSprites
    
    for col = 1, columns do
      local x = startX + (col - 1) * imageSize
      local y = startY - (row - 1) * imageSize
      
      local imageTemp = newObject(bgImageNode);
      imageTemp.transform:SetParent(bgRootTransform, false);
      local rectTransform = imageTemp:GetComponent("RectTransform");
      rectTransform.anchoredPosition = Vector2(x, y)
      local imageRawImage = imageTemp:GetComponent("RawImage");
      
      local spriteName = spriteRow[col];
      TextureManager.SetPng(ETextureUseType.UI, spriteName, imageRawImage);

    end
  end
  destroy(bgImageNode);
end

function UIArmyBattle.InitializePage()
  for i = 1, 6 do
    functionObjs[i] = {};
    functionObjs[i].redDot = functionRoot:Find(string.Concat("Function (", i, ")")):Find("Image_Dot"):GetComponent("Image");
    functionObjs[i].event = functionRoot:Find(string.Concat("Function (", i, ")")):GetComponent("UIEvent");
    functionObjs[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Function);
    functionObjs[i].event.parameter = i;

    functionObjs[i].redDot.enabled = false;
  end

  local pointsCheck = {};
  local lineObj = lineRoot:Find("Line").gameObject;

  for i = 1, TOTAL_CITY_COUNT do
    local dataTemp = ArmyBattle.cityInfo[i];
    cityItems[i].gameObject:SetActive(dataTemp ~= nil);

    if dataTemp ~= nil then
      cityItems[i].transform.localPosition = Vector2.New(dataTemp:GetPos());
      cityItems[i].city.sprite = dataTemp:GetCitySprite();
      cityItems[i].city:SetNativeSize();
      for j = 1, 2 do
        cityItems[i].cityFire[j]:StopChangeTexture();
        cityItems[i].cityFire[j].enabled = false;
      end
      cityItems[i].rectTransform.sizeDelta = cityItems[i].cityRect.sizeDelta;
      cityItems[i].sliderRect.sizeDelta = Vector2.New(this.HPLENGTH * dataTemp.count * ArmyBattle.DEFENCE_LIFE, HPHEIGHT);
      cityItems[i].cityRect.localScale = dataTemp:GetRotation();
      cityItems[i].sliderRect.localPosition = Vector3.New(cityItems[i].sliderRect.localPosition.x, cityItems[i].sliderRect.localPosition.y + dataTemp:GetSliderPosY(), cityItems[i].sliderRect.localPosition.z);
      cityItems[i].buttonRect.localPosition = Vector3.New(cityItems[i].buttonRect.localPosition.x, cityItems[i].buttonRect.localPosition.y + dataTemp:GetButtonPosY(), cityItems[i].buttonRect.localPosition.z);
      cityItems[i].text_Index.text = dataTemp.id;

      local points = dataTemp:GetLine();

      for j = 1, table.Count(points) do
        if not ArmyBattle.LineRepeat(pointsCheck, points[j].pos) then
          local line = {};
          for _, v in pairs(points[j].pos) do
            table.insert(line, Vector3.New(v[1], v[2], 0));
          end

          local lineTemp = newObject(lineObj):GetComponent("LineRenderer");
          lineTemp.transform:SetParent(lineRoot);
          lineTemp:GetComponent("RectTransform").localScale = Vector2.New(1, 1);
          lineTemp:SetPositions(line);
          table.insert(cityItems[i].lineInfo.lines, lineTemp);
          table.insert(cityItems[i].lineInfo.index, points[j].index);

          table.insert(pointsCheck, points[j].pos);
        end
      end
    end
  end

  destroy(lineObj);
end

function UIArmyBattle.OnOpen()
  UINewChat.SetOrder(this.uiController.canvas.sortingOrder + 5);
  gameObject_MultiCity.rectTransform.localScale = Vector2.New(1, 1);
  gameObject_MultiCity.rectTransform.anchorMin = Vector2.New(0.5, 0.5);
  gameObject_MultiCity.rectTransform.anchorMax = Vector2.New(0.5, 0.5);
  gameObject_MultiCity.rectTransform.pivot = Vector2.New(0.5, 0.5);
  gameObject_MultiCity.rectTransform.anchoredPosition = Vector2.zero;
  gameObject_Plus:SetActive(false);

  this.UpdateUI();


  if not CGTimer.ContainsListener(this.UpdateActivityTime) then
    CGTimer.AddListener(this.UpdateActivityTime, 1);
  end

  return true;
end

function UIArmyBattle.OnClose()
  UINewChat.SetOrder();

  for i = 1, table.Count(ArmyBattle.teamInfos) do
    if armyInfo[i].ensignTexture ~= nil then
      armyInfo[i].ensignTexture = nil;
    end
  end

  for i = 1, TOTAL_CITY_COUNT do
    if CGTimer.ContainsListener(cityItems[i].timerEvent) then
      CGTimer.RemoveListener(cityItems[i].timerEvent);
      ArmyBattle.ResetRebuildTime(i);
    end
  end

  CGTimer.RemoveListener(this.UpdateActivityTime);
  CGTimer.RemoveListener(this.UpdateAttackCountTime);

  return true;
end

function UIArmyBattle.UpdateUI()
  this.UpdateMap();
  this.UpdateRedDot();

  text_Status.text = ArmyBattle.GetStatusString();

  for i = 1, 2 do
    if Organization.GetEnsign(ArmyBattle.teamInfos[i].id) ~= nil then
      armyInfo[i].ensignTexture = Organization.GetEnsign(ArmyBattle.teamInfos[i].id);
    else
      armyInfo[i].ensignTexture = Organization.sampleEnsigns[1].texture;
    end

    if ArmyBattle.teamInfos[i].id ~= 0 then
      armyInfo[i].name.text = ArmyBattle.teamInfos[i].name;
    else
      armyInfo[i].name.text = string.Get(98380);
    end

    armyInfo[i].infoEnsign.texture = armyInfo[i].ensignTexture;
    armyInfo[i].count.text = string.format(string.Get(24239), ArmyBattle.GetTotalCount(i));

    for j = 1, orderCount do
      armyInfo[i].orders[j].gameObject:SetActive(ArmyBattle.teamInfos[i].orderCount >= j);
      armyInfo[i].orders[j].text_Index.text = ArmyBattle.teamInfos[i].orders[j];
    end
  end

  gameObject_Plus:SetActive(ArmyBattle.playerAttackCount == 0 and
    ArmyBattle.battleStatus == ArmyBattleStatus.Attack);

  if ArmyBattle.playerDefencePoint ~= 0 then
    text_Defence.text = string.format(string.Get(24240), ArmyBattle.playerDefencePoint);
  else
    text_Defence.text = string.format(string.Get(24240), "");
  end
  text_AttackCount.text = string.format(string.Get(24241), ArmyBattle.playerAttackCount, ArmyBattle.GetTotalAtkCount());
  text_PlayerScore.text = string.format(string.Get(24242), ArmyBattle.playerScore);

  if not CGTimer.ContainsListener(this.UpdateActivityTime) then
    CGTimer.AddListener(this.UpdateActivityTime, 1);
  end

  if not CGTimer.ContainsListener(this.UpdateAttackCountTime) then
    CGTimer.AddListener(this.UpdateAttackCountTime, 1);
  end
end

function UIArmyBattle.UpdateMap()
  for i = 1, TOTAL_CITY_COUNT do
    local dataTemp = ArmyBattle.cityInfo[i];
    if dataTemp ~= nil then
      cityItems[i].city.sprite = dataTemp:GetCitySprite();
      cityItems[i].flag.sprite = dataTemp:GetFlagSprite();
      cityItems[i].sliderColor.sprite = dataTemp:GetHPSprite();
      cityItems[i].cityRect.localScale = dataTemp:GetRotation();

      for j = 1, 2 do
        cityItems[i].cityFire[j].enabled = ArmyBattle.SliderValue(i) <= 0.5;

        if ArmyBattle.SliderValue(i) <= 0.5 then
        cityItems[i].cityFire[j]:ContinuedPlay(tostring(fireId), 1, 4, 0.1, -1);
        else
          cityItems[i].cityFire[j]:StopChangeTexture();
        end
      end

      if dataTemp.id == ArmyBattle.playerDefencePoint then
        cityItems[i].text_Index.text = string.Get(20406);
      else
        cityItems[i].text_Index.text = dataTemp.id;
      end

      cityItems[i].hpSlider.value = ArmyBattle.SliderValue(i);
      if dataTemp.moral ~= ArmyMoralColor.Yellow then
        cityItems[i].text_defence.text = string.format(string.Get(24253), dataTemp.nowCount, dataTemp.count);
      else
        cityItems[i].text_defence.text = "";
      end
      cityItems[i].text_defence.enabled = false;
      cityItems[i].text_Button.text = ArmyBattle.GetDefAtkString(dataTemp.moral);
      cityItems[i].button:SetActive(false);
      cityItems[i].text_status.enabled = true;
      cityItems[i].image_fight.enabled = dataTemp.isFighting;
      cityItems[i].text_status.enabled = ArmyBattle.CheckTime(dataTemp.rebuild);
      cityItems[i].timerEvent = function()
        local needTimeMoral = (ArmyBattle.playerMoralColor == dataTemp.moral) or (dataTemp.moral == ArmyMoralColor.Yellow);
        if needTimeMoral and ArmyBattle.CheckTime(dataTemp.rebuild) then
          cityItems[i].text_status.text = string.Concat(string.Get(24270), string.GetSecondSymbol(math.ceil(DateTime.Subtract(dataTemp.rebuild, CGTimer.serverTime).TotalSeconds), true));
        else
          cityItems[i].text_status.text = string.Get(24270);
        end

        cityItems[i].text_status.enabled = ArmyBattle.CheckTime(dataTemp.rebuild);

        if not ArmyBattle.CheckTime(dataTemp.rebuild) then
          if CGTimer.ContainsListener(cityItems[i].timerEvent) then
            ArmyBattle.ResetRebuildTime(i);
            CGTimer.RemoveListener(cityItems[i].timerEvent);
          end
        end
      end

      if ArmyBattle.CheckTime(dataTemp.rebuild) then
        if not CGTimer.ContainsListener(cityItems[i].timerEvent) then
          CGTimer.AddListener(cityItems[i].timerEvent, 1);
        end
      end

      for k, v in pairs(cityItems[i].lineInfo.lines) do
        if ArmyBattle.IsRoadIgnore(ArmyBattle.playerMoralColor) then
          v.startColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);

          if ArmyBattle.IsRoadIgnore(ArmyBattle.GetEnemyColor()) then
            v.endColor = ArmyBattle.GetColor();
          else
            v.endColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
          end
        else
          if ArmyBattle.IsRoadIgnore(ArmyBattle.GetEnemyColor()) then
            if not ArmyBattle.IsEnemy(dataTemp.moral) then
              v.startColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
            else
              if ArmyBattle.cityInfo[cityItems[i].lineInfo.index[k]].moral == ArmyBattle.playerMoralColor then
                v.startColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
              else
                v.startColor = ArmyBattle.GetColor();
              end
            end

            v.endColor = ArmyBattle.GetColor();
          else
            if not ArmyBattle.IsEnemy(dataTemp.moral) then
              v.startColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
              v.endColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
            else
              if ArmyBattle.cityInfo[cityItems[i].lineInfo.index[k]].moral == ArmyBattle.playerMoralColor then
                v.startColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
                v.endColor = ArmyBattle.GetColor(ArmyBattle.playerMoralColor);
              else
                v.startColor = ArmyBattle.GetColor();
                v.endColor = ArmyBattle.GetColor();
              end
            end
          end
        end
      end
    end
  end
end

function UIArmyBattle.UpdateActivityTime()
  if ArmyBattle.CheckTime(ArmyBattle.battleStatusTime) then
    text_ActivityTime.text = string.GetSecondText(math.ceil(DateTime.Subtract(ArmyBattle.battleStatusTime, CGTimer.serverTime).TotalSeconds));
  else
    text_ActivityTime.text = string.Concat("0", string.Get(40145));
    CGTimer.RemoveListener(this.UpdateActivityTime);
  end
end

function UIArmyBattle.UpdateAttackCountTime()
  if ArmyBattle.CheckTime(ArmyBattle.playerAttackCountTime) then
    text_AttackCountTime.enabled = true;
    text_AttackCountTime.text = string.GetSecondSymbol(math.ceil(DateTime.Subtract(ArmyBattle.playerAttackCountTime, CGTimer.serverTime).TotalSeconds));
  else
    text_AttackCountTime.enabled = false;
    CGTimer.RemoveListener(this.UpdateAttackCountTime);
  end
end

function UIArmyBattle.UpdateRedDot()
  functionObjs[3].redDot.enabled = RedDot.Check(ERedDot.ArmyBattleTeam);
end

  --region 拖拉
local clampMin = Vector2.zero;
local clampMax = Vector2.zero;

function UIArmyBattle.OnPress_Panel(uiEvent)
  panelStartPosition = ConvertPositionToUIScene(this.uiController.transform, uiEvent.eventData.position, this.uiController.canvas.worldCamera);
  multiPos = gameObject_MultiCity.rectTransform.anchoredPosition;
  screenW = math.ceil(UI.width/2);
  screenH = math.ceil(UI.height/2);
  clampMin.x = -MAPWIDTH / 2 + screenW;
  clampMax.x = MAPWIDTH / 2 - screenW;
  clampMin.y = -MAPHEIGHT / 2 + screenH;
  clampMax.y = MAPHEIGHT / 2 - screenH;
end

function UIArmyBattle.OnRelease_Panel()

end

function UIArmyBattle.OnDrag_Panel(uiEvent)
  local touchPosition = ConvertPositionToUIScene(this.uiController.transform, uiEvent.eventData.position, this.uiController.canvas.worldCamera);

  if touchPosition == panelStartPosition then return end

  moveDirection = touchPosition - panelStartPosition;
  if moveDirection.magnitude < minTouchMoveValue then return end

  local pos = multiPos + moveDirection;
  pos.x = Mathf.Clamp(pos.x, clampMin.x, clampMax.x);
  pos.y = Mathf.Clamp(pos.y, clampMin.y, clampMax.y);
  panelStartPosition = touchPosition;

  multiPos = pos;
  gameObject_MultiCity.rectTransform.anchoredPosition = pos;

  if not hasDragged then
    hasDragged = true;
  end
end
  --endregion

function UIArmyBattle.OnClick_Help()
  UI.Open(UITutorialHint, 0, this, string.Get(24232), string.Get(24381));
end

function UIArmyBattle.OnClick_Function(uiEvent)
  if UI.IsVisible(UITutorialHint) then return end

  local kind = uiEvent.parameter;

  sendBuffer:Clear();

  if kind == 1 then
    UI.Close(this);
    --C:171-027 <離開活動>
    Network.Send(171, 27);
  elseif kind == 2 then
    UI.Open(UIArmy);
  elseif kind == 3 then
    --C:171-005 <取得防守隊伍>
    Network.Send(171, 5);
  elseif kind == 4 then
    --C:171-009 <取得戰令>
    Network.Send(171, 9);
  elseif kind == 5 then
    --C:171-004 <查對戰紀錄>
    Network.Send(171, 4);
  elseif kind == 6 then
    --C:171-101 <排行榜>
    Network.Send(171, 101);
  end
end

function UIArmyBattle.OnClick_City(index)
  if UI.IsVisible(UITutorialHint) then return end

  if hasDragged then
    hasDragged = false;
    return;
  end

  if ArmyBattle.battleStatus == ArmyBattleStatus.Prepare then
    if ArmyBattle.IsEnemy(ArmyBattle.cityInfo[index].moral) then
      ShowCenterMessage(string.Get(24252));
      return;
    elseif ArmyBattle.IsDrumPoint(ArmyBattle.cityInfo[index].moral) then
      ShowCenterMessage(string.Get(24687));
      return;
    end
  end

  if ArmyBattle.battleStatus == ArmyBattleStatus.Result then
    ShowCenterMessage(string.Get(20377));
    return;
  end

  if ArmyBattle.IsEnemy(ArmyBattle.cityInfo[index].moral) then
    local hasRoad = false;

    for i = 1, 6 do
      if ArmyBattle.cityInfo[index].neighbor[i] ~= 0 then
        if not ArmyBattle.IsEnemy(ArmyBattle.cityInfo[ArmyBattle.cityInfo[index].neighbor[i]].moral) then
          hasRoad = true;
          break;
        end
      end
    end

    if (ArmyBattle.GetPointCount(ArmyBattle.playerMoralColor) == 0
      and ArmyBattle.cityInfo[index].selfPoint == ArmyBattle.playerMoralColor)
    or ArmyBattle.IsRoadIgnore(ArmyBattle.playerMoralColor)
    or ArmyBattle.IsDrumPoint(ArmyBattle.cityInfo[index].moral) then
      hasRoad = true;
    end

    if not hasRoad then
      ShowCenterMessage(string.Get(24259));
      return;
    end

    local hasOther = false;

    local neighborCount = 0;
    local roadCount = 0;

    for i = 1, 6 do
      if ArmyBattle.cityInfo[index].neighbor[i] ~= 0 then
        neighborCount = neighborCount + 1;

        if ArmyBattle.CheckTime(ArmyBattle.cityInfo[ArmyBattle.cityInfo[index].neighbor[i]].rebuild) then
          for j = 1, 6 do
            if ArmyBattle.cityInfo[index].neighbor[j] ~= 0 then
              if not ArmyBattle.IsEnemy(ArmyBattle.cityInfo[ArmyBattle.cityInfo[index].neighbor[j]].moral)
                and not ArmyBattle.CheckTime(ArmyBattle.cityInfo[ArmyBattle.cityInfo[index].neighbor[j]].rebuild) then
                hasOther = true;
                break;
              end
            end
          end
        else
          roadCount = roadCount + 1;
        end
      end
    end

    hasRoad = roadCount == neighborCount;

    if hasOther
      or ArmyBattle.IsRoadIgnore(ArmyBattle.playerMoralColor)
      or ArmyBattle.IsDrumPoint(ArmyBattle.cityInfo[index].moral) then
      hasRoad = true;
    end

    if not hasRoad then
      ShowCenterMessage(string.Get(24398));
      return;
    end
  end

  for i = 1, TOTAL_CITY_COUNT do
    if i == index then
      local show = not cityItems[i].button.activeSelf;

      local dataTemp = ArmyBattle.cityInfo[i];
      if dataTemp ~= nil then
        if not ArmyBattle.IsEnemy(dataTemp.moral) then
          cityItems[i].button:SetActive(show);
          cityItems[i].text_defence.enabled = show;
        else
          cityItems[i].button:SetActive(show and not ArmyBattle.CheckTime(dataTemp.rebuild));
          cityItems[i].text_defence.enabled = show and not ArmyBattle.CheckTime(dataTemp.rebuild);

          if ArmyBattle.CheckTime(dataTemp.rebuild) then
            ShowCenterMessage(string.Get(24270));
          end
        end
      end
    else
      if cityItems[i].button.activeSelf then
        cityItems[i].button:SetActive(false);
        cityItems[i].text_defence.enabled = false;
      end
    end
  end
end

function UIArmyBattle.OnClick_Button(index)
  ArmyBattle.nowChoosePoint = index;

  --C:171-008 <選擇城池> +城池ID(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(index);
  Network.Send(171, 8, sendBuffer);
end

function UIArmyBattle.OnClick_Plus()
  UISell.Launch(63);
end

function UIArmyBattle.UpdateChoose()
  if ArmyBattle.IsEnemy(ArmyBattle.cityInfo[ArmyBattle.nowChoosePoint].moral)
  or ArmyBattle.IsDrumPoint(ArmyBattle.cityInfo[ArmyBattle.nowChoosePoint].moral) then
    UI.Open(UIArmyBattleDefence, EArmyBattleFrameType.Attack);
  else
    UI.Open(UIArmyBattleDefence, EArmyBattleFrameType.Defence);
  end

  cityItems[ArmyBattle.nowChoosePoint].button:SetActive(false);
  cityItems[ArmyBattle.nowChoosePoint].text_defence.enabled = false;
end
