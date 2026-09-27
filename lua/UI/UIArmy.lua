
EUIArmyFunction = {
  None = 0,
  Declaration = 1,
  Sign = 2,
}

EUIArmyPage = {
  Info = 1,
  Member = 2,
  Invite = 3,
  Ensign = 4,
  Resource = 5,
  Army = 6,
  Weapon = 7,
  Ally = 8,
  Boss = 9,
  OrgWar = 10,
  Production = 11,
  SupportInfo = 12,
  SupportMission = 13,
  SupportPoke = 14,
}

UIArmy = {}
local this = UIArmy;

this.name = "UIArmy";
this.uiController = nil;

--ui
local transform_FrameRect;
local gameObjects = {};

--Info

local infoTexts = {};

local image_Signed;
local image_FunctionContentBG;

local rawImage_InfoEnsign;
local rawImage_WeekScoreAwardBG;
local rawImage_WeekScoreAward;

local inputField_Declaration;

local slider_Score;

local event_Declaration;
local event_Invite;
local event_FunctionBG;

--WeekScore
local weekAwards = {};

--Member
local text_SelectInfo;
local text_SelectAll;
local text_InfoDuty;

local image_MemberBG;

local scrollContent_Member;
local scrollItems_Member = {};

local event_SelectClear;

--Ensign
local image_SelectPixel;
local image_SelectColor;

local rawImage_Ensign;
local rawImage_PreviewEnsign;

local scrollContent_Ensign;
local scrollItems_Ensign = {};

local event_Colors = {};

--Resource
local text_Donate;
local text_Resources = {};

local inputField_Donate;

--Production
local productions = {};

--Army
local rawImage_Armys = {};
local text_Armys = {};

local event_ArmyDuty;

--Weapon
local weapons = {};

--Boss
local rawImage_Boss = {};

local text_BossLv;
local text_BossHp;
local text_BossCooldown;
local text_BossTimes;

local slider_BossHp;

local scrollContent_BossRank;
local scrollItems_BossRank = {};

local item_BossAward = {};
local event_BossFight;

--Ally
local image_AllySwitch;

local scrollContent_Ally;
local scrollItems_Ally = {};

--Support
local pokeGroup = {};

local text_canPoke;
local text_pokeTime;
local text_supportTime;
local text_PokeCount;
local text_TotalScore;
local text_MissionTime;
local text_SelfScore;
local text_GetAward;
local image_Receive;

local slider_ArmyScore;

local scrollContent_MissionAward;
local scrollItems_MissionAward = {};
local scrollContent_SupportRank;
local scrollItems_SupportRank = {};
local scrollContent_SupportAwards;
local scrollItems_SupportAwards = {};
local scrollContent_Message;
local scrollItems_Message = {};

--var
local currentPage = EUIArmyPage.Info;
local currentFunction = EUIArmyFunction.None;
local currentProductions = {};
local currentDuty = EOrgDuty.None;
local supportCanClick = true;

local tagDatas = {};
local pageTagInfos = {};
local datas = {};
local supportData = {};
local rankData = {};
local scoreData = {};

local donateResources = {};
local donateWeapons = {};

--ensign
local pixelScale = 15;
local currentSample = 0;
local currentX = 0;
local currentY = 0;
local ensignTexture = nil;
local ensignData = {};

function UIArmy.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  this.uiController.onClose = this.OnClose;

  local tempTransform;
  local tempEvent;
  local tempText;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  gameObjects.Main = uiController:FindGameObject("Org");
  gameObjects.Member = uiController:FindGameObject("Member");
  gameObjects.Resource = uiController:FindGameObject("Resource");
  gameObjects.Production = uiController:FindGameObject("Production");
  gameObjects.Army = uiController:FindGameObject("Army");
  gameObjects.Weapon = uiController:FindGameObject("Weapon");
  gameObjects.Ensign = uiController:FindGameObject("Ensign");
  gameObjects.Ally = uiController:FindGameObject("Ally");
  gameObjects.Declaration = uiController:FindGameObject("Declaration");
  gameObjects.Sign = uiController:FindGameObject("Sign");
  gameObjects.SignRedDot = uiController:FindGameObject("Image_SignRedDot");
  gameObjects.WeekAward = uiController:FindGameObject("Image_WeekAwardBG");
  gameObjects.Boss = uiController:FindGameObject("Boss");
  gameObjects.SupportPoke = uiController:FindGameObject("SupportPoke");
  gameObjects.SupportInfo = uiController:FindGameObject("SupportInfo");
  gameObjects.SupportMission = uiController:FindGameObject("SupportMission");

    --region Info
  infoTexts.text_Name = uiController:FindText("Text_Name");
  infoTexts.text_Level = uiController:FindText("Text_Level");
  infoTexts.text_CreateTime = uiController:FindText("Text_CreateTime");
  infoTexts.text_LeaderName = uiController:FindText("Text_LeaderName");
  infoTexts.text_MemberCount = uiController:FindText("Text_MemberCount");
  infoTexts.text_OnlineCount = uiController:FindText("Text_OnlineCount");
  infoTexts.text_AllyName = uiController:FindText("Text_AllyName");
  infoTexts.text_Declaration = uiController:FindText("Text_Declaration");
  infoTexts.text_WeekScore = uiController:FindText("Text_WeekScore");
  infoTexts.text_Score = uiController:FindText("Text_Score");
  infoTexts.text_Exp = uiController:FindText("Text_Exp");

  rawImage_InfoEnsign = uiController:FindRawImage("RawImage_InfoEnsign");
  rawImage_WeekScoreAwardBG = uiController:FindRawImage("RawImage_WeekScoreAwardBG");
  rawImage_WeekScoreAward = uiController:FindRawImage("RawImage_WeekScoreAward");

  inputField_Declaration = uiController:FindInputField("InputField_Declaration"):GetComponent("UIInputField");
  inputField_Declaration:AddListener(this.OnChange_Declaration);

  slider_Score = uiController:FindSlider("Slider_Score");

  event_Declaration = uiController:FindEvent("Image_Declaration");
  event_Declaration:SetListener(EventTriggerType.PointerClick, this.OnClick_Declaration);

  tempEvent = uiController:FindEvent("Image_Sign");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Sign);

  tempEvent = uiController:FindEvent("Image_Leaderboard");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Leaderboard);
  
  event_Invite = uiController:FindEvent("Image_Invite");
  event_Invite:SetListener(EventTriggerType.PointerClick, this.OnClick_Invite);

  tempEvent = uiController:FindEvent("Image_Quit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Quit);

  --endregion

    --region WeekAward
  tempTransform = uiController:FindGameObject("Image_WeekAwardContentBG").transform;
  for i = 1, 6 do
    weekAwards[i] = {};
    weekAwards[i].transform = tempTransform:Find(string.Concat("Image_WeekAward (", i, ")"));
    weekAwards[i].image_BG = weekAwards[i].transform:Find("Image_BG"):GetComponent("Image");
    weekAwards[i].text_WeekScore = weekAwards[i].transform:Find("Text_WeekScore"):GetComponent("Text");
    weekAwards[i].item_Award = UIItemController.New(weekAwards[i].transform:Find("Image_ItemBG"));
  end

  tempEvent = uiController:FindEvent("RawImage_WeekScoreAward");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_WeekAwardSwitch);
  tempEvent.parameter = true;

  tempEvent = uiController:FindEvent("Image_CloseWeekAward");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_WeekAwardSwitch);
  tempEvent.parameter = false;

  --Function
  this.InitFunction(uiController);
  
  --Member
  gameObjects.InfoScore = uiController:FindGameObject("Text_InfoScore");
  gameObjects.InfoWeekScore = uiController:FindGameObject("Text_InfoWeekScore");
  gameObjects.SelectInfo = uiController:FindGameObject("SelectInfo");

  image_MemberBG = uiController:FindImage("Image_MemberBG");
  text_SelectInfo = uiController:FindText("Text_SelectInfo");
  text_SelectAll = uiController:FindText("Text_SelecetAll");
  text_InfoDuty = uiController:FindText("Text_InfoDuty");

  scrollContent_Member = uiController:FindScrollContent("ScrollContent_Member");
  scrollContent_Member.onInitialize = this.OnInitialize_ScrollContent_Member;
  scrollContent_Member.onItemChange = this.OnItemChange_ScrollContent_Member;
  scrollContent_Member:Initialize(1);

  tempEvent = uiController:FindEvent("Image_SelectAll");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectAll);
  tempEvent.parameter = true;

  event_SelectClear = uiController:FindEvent("Image_SelectClear");
  event_SelectClear:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectAll);
  event_SelectClear.parameter = false;
  --endregion

  --Ensign
  this.InitEnsign(uiController);

  --Resource
  this.InitResource(uiController);

  --Production
  this.InitProduction(uiController);

    --region Army
  for i = 1, City.maxArmy do
    tempTransform = gameObjects.Army.transform:Find(string.Concat("Image_Army (", i, ")"));

    rawImage_Armys[i] = tempTransform:Find("RawImage_Army"):GetComponent("RawImage");
    text_Armys[i] = tempTransform:Find("Text_Army"):GetComponent("Text");

    tempEvent = rawImage_Armys[i].transform:GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_NpcInfo);
    tempEvent.parameter = City.armyNpcId[i];

    tempText = tempTransform:Find("Text_"):GetComponent("Text");
    tempText.text = npcDatas[City.armyNpcId[i]].name;
  end

  event_ArmyDuty = uiController:FindEvent("Image_Duty");
  event_ArmyDuty:SetListener(EventTriggerType.PointerClick, this.OnClick_ArmyDuty);
  --endregion

    --region Weapon
  for i = 1, City.maxWeapon do
    weapons[i] = {};
    tempTransform = gameObjects.Weapon.transform:Find(string.Concat("Image_Weapon (", i, ")"));

    weapons[i].rawImage_Weapon = tempTransform:Find("RawImage_Weapon"):GetComponent("RawImage");
    weapons[i].text_Weapon = tempTransform:Find("Text_Weapon"):GetComponent("Text");

    tempEvent = weapons[i].rawImage_Weapon.transform:GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_NpcInfo);
    tempEvent.parameter = City.weaponNpcId[i];

    weapons[i].event_Weapon = tempTransform:Find("Image_Weapon"):GetComponent("UIEvent");
    weapons[i].event_Weapon:SetListener(EventTriggerType.PointerClick, this.OnClick_Weapon);
    weapons[i].event_Weapon.parameter = i;
  end

  tempEvent = uiController:FindEvent("Image_DonateWeapon");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_DonateWeapon);
  --endregion

    --region Boss
  for i = 1, 2 do
    rawImage_Boss[i] = uiController:FindRawImage(string.Concat("RawImage_Boss (", i, ")"));
  end

  text_BossLv = uiController:FindText("Text_BossLv");
  text_BossHp = uiController:FindText("Text_BossHp");
  text_BossCooldown = uiController:FindText("Text_BossCooldown");
  text_BossTimes = uiController:FindText("Text_BossTimes");
  slider_BossHp = uiController:FindSlider("Slider_BossHp");

  event_BossFight = uiController:FindEvent("Image_BossFight");
  event_BossFight:SetListener(EventTriggerType.PointerClick, this.OnClick_BossFight);

  scrollContent_BossRank = uiController:FindScrollContent("ScrollContent_BossRank");
  scrollContent_BossRank.onInitialize = this.OnInitialize_ScrollContent_BossRank;
  scrollContent_BossRank.onItemChange = this.OnItemChange_ScrollContent_BossRank;
  scrollContent_BossRank:Initialize(1);

  tempTransform = uiController:FindGameObject("BossAwards").transform;
  for i = 1, 5 do
    item_BossAward[i] = UIItemController.New(tempTransform:Find(string.Concat("Image_BossAward (", i, ")")));
  end
  --endregion

    --region Ally
  image_AllySwitch = uiController:FindImage("Image_AllySwitch");

  scrollContent_Ally = uiController:FindScrollContent("ScrollContent_Ally");
  scrollContent_Ally.onInitialize = this.OnInitialize_ScrollContent_Ally;
  scrollContent_Ally.onItemChange = this.OnItemChange_ScrollContent_Ally;
  scrollContent_Ally:Initialize(1);

  tempEvent = uiController:FindEvent("Image_AllySwitch");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AllySwitch);

  tempEvent = uiController:FindEvent("Image_AllyRefresh");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AllyRefresh);
    --endregion

  --Support
  this.InitSupportPoke(uiController);
  this.InitSupportInfo(uiController);
  this.InitSupportMission(uiController);
  ArmySupport.Init();
end

  --region Init
function UIArmy.InitResource(uiController)
  local tempTransform;
  local tempEvent;
  text_Donate = uiController:FindText("Text_Donate");
  
  inputField_Donate = uiController:FindInputField("InputField_Donate"):GetComponent("UIInputField");
  inputField_Donate:AddListener(this.OnChange_Donate);
  
  for i = 1, Organization.maxResource do
    tempTransform = gameObjects.Resource.transform:Find(string.Concat("Image_Resource (", i, ")"));
    
    text_Resources[i] = tempTransform:Find("Text_Resources"):GetComponent("Text");
  end
  
  tempEvent = uiController:FindEvent("Image_DonateResource");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_DonateResource);
end

function UIArmy.InitProduction(uiController)
  local tempTransform;
  local tempEvent;

  for k, v in pairs(EOrgProduction) do
    tempTransform = gameObjects.Production.transform:Find(string.Concat("Production (", v, ")"));

    productions[v] = {};
    productions[v].text_Unit = tempTransform:Find("Text_Unit"):GetComponent("Text");
    productions[v].rawImage_Unit = tempTransform:Find("RawImage_Unit"):GetComponent("RawImage");

    productions[v].event_Prev = tempTransform:Find("Image_Prev"):GetComponent("UIEvent");
    productions[v].event_Prev:SetListener(EventTriggerType.PointerClick, this.OnClick_ProductPrev);
    productions[v].event_Prev.parameter = v;

    productions[v].event_Next = tempTransform:Find("Image_Next"):GetComponent("UIEvent");
    productions[v].event_Next:SetListener(EventTriggerType.PointerClick, this.OnClick_ProductNext);
    productions[v].event_Next.parameter = v;

    tempEvent = tempTransform:Find("Image_Take"):GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ProductTake);
    tempEvent.parameter = v;

    productions[v].text_Take = tempEvent.transform:Find("Text_Take"):GetComponent("Text");

    productions[v].text_Requires = {};
    tempTransform = tempTransform:Find("Require");
    for i = 1, 6 do
      productions[v].text_Requires[i] = tempTransform:Find(string.Concat("Image_Require (", i, ")")):Find("Text_Require"):GetComponent("Text");
    end

    productions[v].timerEvent = nil;
  end
end

function UIArmy.InitEnsign(uiController)
  local tempTransform;
  local tempEvent;

  for i = 1, Organization.ensignDataSize do
    ensignData[i] = 255;
  end

  image_SelectPixel = uiController:FindImage("Image_PixelSelect");
  image_SelectColor = uiController:FindImage("Image_ColorSelect");

  rawImage_Ensign = uiController:FindRawImage("RawImage_Ensign");
  rawImage_PreviewEnsign = uiController:FindRawImage("RawImage_PreviewEnsign");

  scrollContent_Ensign = uiController:FindScrollContent("ScrollContent_Ensign");
  scrollContent_Ensign.onInitialize = this.OnInitialize_ScrollContent_Ensign;
  scrollContent_Ensign.onItemChange = this.OnItemChange_ScrollContent_Ensign;
  scrollContent_Ensign:Initialize(1);

  tempTransform = uiController:FindGameObject("Image_ColorBG").transform;
  for i = 0, 15 do
    event_Colors[i] = tempTransform:Find(string.Concat("Image_Color (", (i + 1), ")")):GetComponent("UIEvent");
    event_Colors[i]:SetListener(EventTriggerType.PointerClick, this.OnClick_Color);
    event_Colors[i].parameter = i;
  end

  tempEvent = rawImage_Ensign.transform:GetComponent("UIEvent");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Ensign);

  tempEvent = uiController:FindEvent("Image_PixelUp");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PixelUp);

  tempEvent = uiController:FindEvent("Image_PixelRight");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PixelRight);

  tempEvent = uiController:FindEvent("Image_PixelDown");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PixelDown);

  tempEvent = uiController:FindEvent("Image_PixelLeft");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PixelLeft);

  tempEvent = uiController:FindEvent("Image_EnsignSubmit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_EnsignSubmit);

  tempEvent = uiController:FindEvent("Image_EnsignReset");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_EnsignReset);

  tempEvent = uiController:FindEvent("Image_SaveEnsign");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_EnsignSave);
end

function UIArmy.InitFunction(uiController)
  local tempEvent;

  infoTexts.text_Function = uiController:FindText("Text_T1_Function");
  infoTexts.text_NextSignLv = uiController:FindText("Text_NextSignLv");
  infoTexts.text_NextSignRequire1 = uiController:FindText("Text_NextSignRequire (1)");
  infoTexts.text_NextSignRequire2 = uiController:FindText("Text_NextSignRequire (2)");
  infoTexts.text_NextSignRequire3 = uiController:FindText("Text_NextSignRequire (3)");
  infoTexts.text_CurrentSignLv = uiController:FindText("Text_CurrentSignLv");
  infoTexts.text_CurrentSignRequire1 = uiController:FindText("Text_CurrentSignRequire (1)");
  infoTexts.text_CurrentSignRequire2 = uiController:FindText("Text_CurrentSignRequire (2)");
  infoTexts.text_CurrentSignRequire3 = uiController:FindText("Text_CurrentSignRequire (3)");

  image_Signed = uiController:FindImage("Image_Signed");
  image_FunctionContentBG = uiController:FindImage("Image_FunctionContentBG");

  event_FunctionBG = uiController:FindEvent("Image_FunctionBG");
  event_FunctionBG:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseFunction);

  tempEvent = uiController:FindEvent("Image_FunctionClose");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseFunction);

  tempEvent = uiController:FindEvent("Image_DeclarationSubmit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_DeclarationSubmit);

  tempEvent = uiController:FindEvent("Image_SignGet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SignSubmit);
end

function UIArmy.InitSupportPoke(uiController)
  text_canPoke = uiController:FindText("Text_PokeCount");
  text_pokeTime = uiController:FindText("Text_PokeTime");
  
  scrollContent_Message = uiController:FindScrollContent("ScrollContent_Message");
  scrollContent_Message.onInitialize = this.OnInitialize_scrollContent_Message;
  scrollContent_Message.onItemChange = this.OnItemChange_scrollContent_Message;
  scrollContent_Message:Initialize(1);
  
  local tempTransform = gameObjects.SupportPoke.transform:Find("Image_SupportPokeBG").transform;
  local obj = tempTransform:Find("Image_Item").gameObject;

  for i = 1, ArmySupport.pokeMaxCount do
    pokeGroup[i] = {};
    local tempObj = newObject(obj);
    tempObj.transform:SetParent(tempTransform);
    tempObj:GetComponent("RectTransform").localScale = Vector3.one;

    pokeGroup[i].image_BG = tempObj:GetComponent("Image");
    pokeGroup[i].image_Poke = pokeGroup[i].image_BG.transform:Find("PokeRoot"):GetComponent("Image");
    pokeGroup[i].image_Poke.enabled = false;
  end

  destroy(obj);
  
  local tempEvent = uiController:FindEvent("Image_Poke");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Poke);
  
  tempEvent = uiController:FindEvent("Image_ShowPrice");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ShowPrice);

  tempEvent = uiController:FindEvent("Image_Refresh");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Refresh);

  image_Receive = uiController:FindEvent("Image_Receive").transform:GetComponent("Image");
  tempEvent = uiController:FindEvent("Image_Receive");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Receive);
end

function UIArmy.InitSupportInfo(uiController)
  slider_ArmyScore = uiController:FindSlider("Slider_ArmyScore");
  text_TotalScore = uiController:FindText("Text_TotalScore");
  text_supportTime = uiController:FindText("Text_SupportTime");
  text_PokeCount = uiController:FindText("Text_PokeCount");
  text_MissionTime = uiController:FindText("Text_MissionTime");
  text_SelfScore = uiController:FindText("Text_SelfScore");
  text_GetAward = uiController:FindText("Text_Get");

  scrollContent_SupportRank = uiController:FindScrollContent("ScrollContent_SupportRank");
  scrollContent_SupportRank.onInitialize = this.OnInitialize_ScrollContent_SupportRank;
  scrollContent_SupportRank.onItemChange = this.OnItemChange_ScrollContent_SupportRank;
  scrollContent_SupportRank:Initialize(1);

  scrollContent_SupportAwards = uiController:FindScrollContent("ScrollContent_SupportAwards");
  scrollContent_SupportAwards.onInitialize = this.OnInitialize_ScrollContent_SupportAwards;
  scrollContent_SupportAwards.onItemChange = this.OnItemChange_ScrollContent_SupportAwards;
  scrollContent_SupportAwards:Initialize(1);
end

function UIArmy.InitSupportMission(uiController)
  scrollContent_MissionAward = uiController:FindScrollContent("ScrollContent_MissionAward");
  scrollContent_MissionAward.onInitialize = this.OnInitialize_ScrollContent_MissionAward;
  scrollContent_MissionAward.onItemChange = this.OnItemChange_ScrollContent_MissionAward;
  scrollContent_MissionAward:Initialize(1);
end
  --endregion

function UIArmy.IsPageVisible(page)
  if page == EUIArmyPage.Info then
    return 1, string.Get(21158);
  elseif page == EUIArmyPage.Member then
    return 1, string.Get(10202);
  elseif page == EUIArmyPage.Invite then
    if Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId) then
      return 1, string.Get(10208);
    end
  elseif page == EUIArmyPage.Ensign then
    if Organization.IsChief(Role.playerId) then
      return 1, string.Get(20933);
    end
  elseif page == EUIArmyPage.Resource then
    return 2, string.Get(20931);
  elseif page == EUIArmyPage.Army then
    return 2, string.Get(98048);
  elseif page == EUIArmyPage.Weapon then
    return 2, string.Get(98049);
  elseif page == EUIArmyPage.Ally then
    if Organization.IsChief(Role.playerId) then
      return 1, string.Get(98062);
    end
  elseif page == EUIArmyPage.Boss then
    return 3, string.Get(99790);
  elseif page == EUIArmyPage.OrgWar then
    if Organization.CheckFlag(EOrgFlag.OrgWarRigistered) then
      if Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId) then
        return 3, string.Get(21809);
      end
    end
  elseif page == EUIArmyPage.Production then
    return 2, string.Get(40245);
  elseif ArmySupport.ActivityIsOpen(ArmySupport.supportActID) then
    if page == EUIArmyPage.SupportInfo then
      return 4, string.Get(24404);
    elseif page == EUIArmyPage.SupportMission then
      return 4, string.Get(24405);
    elseif page == EUIArmyPage.SupportPoke then
      return 4, string.Get(24406);
    end
  end

  return nil, nil;
end

function UIArmy.OnOpen(page)
  currentSample = 0;
  ensignTexture = TexturePool.Get(ensignTexture, 24, 24, TextureFormat.RGBA4444);

  currentFunction = EUIArmyFunction.None;

  this.OnClick_EnsignReset();
  ArmySupport.pokeTime = 0;

  if page ~= nil and table.Contains(EUIArmyPage, page) then
    currentPage = page;
  else
    currentPage = EUIArmyPage.Info;
  end

  return true;
end

function UIArmy.OnVisible(visible)
  if visible then
    table.Clear(tagDatas);
    table.Clear(pageTagInfos);

    for page = 1, table.Count(EUIArmyPage) do
      if pageTagInfos[page] == nil then
        pageTagInfos[page] = {};
      end

      local tag, tagText = this.IsPageVisible(page);
      if tag ~= nil and tagText ~= nil then
        if tag > 1 and UI.IsVisible(UIArmyBattle) then break end

        if tagDatas[tag] == nil then
          if tag == 1 then
            tagDatas[tag] = { string.Get(10150), nil };
          elseif tag == 2 then
            tagDatas[tag] = { string.Get(10152), nil };
          elseif tag == 3 then
            tagDatas[tag] = { string.Get(10154), nil };
          elseif tag == 4 and ArmySupport.ActivityIsOpen(ArmySupport.supportActID) then
            tagDatas[tag] = { string.Get(24404), nil };
          end
        end

        if tagDatas[tag][2] == nil then
          tagDatas[tag][2] = {};
        end

        table.insert(tagDatas[tag][2], tagText);
        pageTagInfos[page][1] = tag;
        pageTagInfos[page][2] = table.maxn(tagDatas[tag][2]);
      end
    end

    if pageTagInfos[currentPage][1] == 1 and pageTagInfos[currentPage][2] == 1 then
      currentPage = EUIArmyPage.Info;
    end

    UIFrame.Active(transform_FrameRect, string.Get(20287), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagDatas, pageTagInfos[currentPage][1], pageTagInfos[currentPage][2], this.CheckRedDot);

    this.UpdateUI(true);
  else
    if Contains(currentPage, EUIArmyPage.Resource, EUIArmyPage.Weapon) then
      UI.Close(UIBag);
    end
  end
end

function UIArmy.OnClose()
  if UI.IsVisible(UIAlert) then
    UI.Close(UIAlert);
  end
  
  if ensignTexture ~= nil then
    TexturePool.Recycle(ensignTexture);
    ensignTexture = nil;
  end

  if CGTimer.ContainsListener(this.UpdateSupportTime) then
    CGTimer.RemoveListener(this.UpdateSupportTime);
  end

  if CGTimer.ContainsListener(this.UpdateSupportMissTime) then
    CGTimer.RemoveListener(this.UpdateSupportMissTime);
  end

  inputField_Donate.text = 0;
  ArmySupport.pokedIdx = 0;

  return true;
end

function UIArmy.CheckRedDot(mainTag, subTag)
  local page = 0
  for k, v in pairs(pageTagInfos) do
    if v[1] == mainTag and v[2] == subTag then
      page = k;
      break;
    end
  end
  
  if subTag == nil then
    if mainTag == 1 then
      return RedDot.Check(ERedDot.OrgMemberInvitation, ERedDot.OrgSign);
    elseif mainTag == 2 then
      return RedDot.Check(ERedDot.OrgProduction);
    elseif mainTag == 3 then
      return RedDot.Check(ERedDot.OrgBoss);
    elseif mainTag == 4 then
      return RedDot.Check(ERedDot.ArmySupportInfo, ERedDot.ArmySupportPoke);
    end
  elseif page == EUIArmyPage.Info then
    return RedDot.Check(ERedDot.OrgSign);
  elseif page == EUIArmyPage.Invite then
    return RedDot.Check(ERedDot.OrgMemberInvitation);
  elseif page == EUIArmyPage.Boss then
    return RedDot.Check(ERedDot.OrgBoss);
  elseif page == EUIArmyPage.Production then
    return RedDot.Check(ERedDot.OrgProduction);
  elseif page == EUIArmyPage.SupportInfo then
    return RedDot.Check(ERedDot.ArmySupportInfo);
  elseif page == EUIArmyPage.SupportPoke then
    return RedDot.Check(ERedDot.ArmySupportPoke);
  end

  return false;
end

function UIArmy.UpdateUI(reset)
  if reset then
    UI.Close(UIBag);
  end

  if currentPage == EUIArmyPage.OrgWar then
    currentPage = EUIArmyPage.Member;
    currentDuty = EOrgDuty.OrgWar;
  end
  
  gameObjects.Main:SetActive(Contains(currentPage, EUIArmyPage.Info));
  gameObjects.Member:SetActive(Contains(currentPage, EUIArmyPage.Member));
  gameObjects.Ensign:SetActive(Contains(currentPage, EUIArmyPage.Ensign));
  gameObjects.Resource:SetActive(Contains(currentPage, EUIArmyPage.Resource));
  gameObjects.Army:SetActive(Contains(currentPage, EUIArmyPage.Army));
  gameObjects.Weapon:SetActive(Contains(currentPage, EUIArmyPage.Weapon));
  gameObjects.Ally:SetActive(Contains(currentPage, EUIArmyPage.Ally));
  gameObjects.SignRedDot:SetActive(RedDot.Check(ERedDot.OrgSign));
  gameObjects.Boss:SetActive(Contains(currentPage, EUIArmyPage.Boss));
  gameObjects.Production:SetActive(Contains(currentPage, EUIArmyPage.Production));
  gameObjects.SupportPoke:SetActive(Contains(currentPage, EUIArmyPage.SupportPoke));
  gameObjects.SupportInfo:SetActive(Contains(currentPage, EUIArmyPage.SupportInfo));
  gameObjects.SupportMission:SetActive(Contains(currentPage, EUIArmyPage.SupportMission));
  
  this.UpdateMain();
  this.UpdateMember(reset);
  this.UpdateResource(UIBag.GetSelect(), reset);
  this.UpdateProduction();
  this.UpdateArmy();
  this.UpdateWeapon(UIBag.GetSelect(), reset);
  this.UpdateEnsign();
  this.UpdateBoss(reset);
  this.UpdateAlly(reset);
  this.UpdateFunction();
  this.UpdateSupportInfo(reset);
  this.UpdateSupportMission(reset);
  this.UpdateSupportPoke();

  if reset then
    this.UpdateWeekAward(false);
  end
end

  --region update
function UIArmy.UpdateMain()
  if not Contains(currentPage, EUIArmyPage.Info) then return end

  if Define.IsDebugMode() then
    infoTexts.text_Name.text = string.Concat(Organization.GetOrgName(Organization.Id), "(", Organization.Id, ")");
  else
    infoTexts.text_Name.text = Organization.GetOrgName(Organization.Id);
  end

  infoTexts.text_Level.text = Organization.level;

  local architectureData = armyArchitectureDatas[Organization.level];
  
  infoTexts.text_CreateTime.text = string.Concat(string.Get(40207), Organization.createDate:ToString("yyyy/MM/dd"));
  infoTexts.text_LeaderName.text = Organization.members[Organization.chiefs[1]].name;

  local memberCount = 0;
  local onlineCount = 0;
  for k, v in pairs(Organization.members) do
    memberCount = memberCount + 1;

    if v.online then
      onlineCount = onlineCount + 1;
    end
  end

  infoTexts.text_MemberCount.text = string.Concat(string.Get(10157), "：", memberCount, "/", architectureData.buildings[1].maxMember);
  infoTexts.text_OnlineCount.text = string.Concat(string.Get(10158), "：", onlineCount);

  if string.IsNullOrEmpty(Organization.allyName) then
    infoTexts.text_AllyName.text = string.format(string.Get(22043), string.Get(40400));
  else
    infoTexts.text_AllyName.text = string.format(string.Get(22043), Organization.allyName);
  end

  rawImage_InfoEnsign.texture = ensignTexture;

  TextureManager.SetPng(ETextureUseType.UI, "common0_com075", rawImage_WeekScoreAwardBG);
  TextureManager.SetPng(ETextureUseType.UI, "common0_icon051", rawImage_WeekScoreAward);

  if armyArchitectureDatas[Organization.level + 1] ~= nil then
    slider_Score.maxValue = armyArchitectureDatas[Organization.level + 1].buildings[1].upExp;
  else
    slider_Score.maxValue = architectureData.buildings[1].upExp;
  end

  slider_Score.value = Organization.score;
  infoTexts.text_Exp.text = string.Concat(slider_Score.value, "/", slider_Score.maxValue);

  infoTexts.text_Declaration.text = string.Concat("\n", Organization.declaration);
  
  --Right
  infoTexts.text_Score.text = Organization.score;
  infoTexts.text_WeekScore.text = string.Concat(string.Get(10160), ":", Organization.weekScore);

  inputField_Declaration.text = Organization.declaration;

  if Organization.IsChief(Role.playerId) then
    --text_Quit.text = string.Get(20945);
    event_Declaration.gameObject:SetActive(true);
  else
    --text_Quit.text = string.Get(20942);
    event_Declaration.gameObject:SetActive(false);
  end

  event_Invite.gameObject:SetActive(Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId));
end

function UIArmy.UpdateWeekAward(active)
  active = active and Contains(currentPage, EUIArmyPage.Info);

  gameObjects.WeekAward:SetActive(active);

  if active then
    for i = 1, 6 do
      local armyAwardData = armyAwardDatas[i];
      if armyAwardData ~= nil then
        weekAwards[i].transform.gameObject:SetActive(true);

        if Organization.weekScore >= armyAwardData.requireScore then
          LuaHelper.SetColor(weekAwards[i].image_BG, Color.VeryLightGreen);
        else
          LuaHelper.SetColor(weekAwards[i].image_BG, Color.LightGray);
        end

        weekAwards[i].text_WeekScore.text = armyAwardData.requireScore;
        weekAwards[i].item_Award:SetItem(armyAwardData.awardItemId);
      else
        weekAwards[i].transform.gameObject:SetActive(false);
      end
    end
  end
end

function UIArmy.UpdateMember(reset)
  if not Contains(currentPage, EUIArmyPage.Member) then return end

  table.Clear(datas);
  
  for k, roleId in pairs(Organization.chiefs) do
    if roleId ~= 0 then
      table.insert(datas, roleId);
    end
  end

  for roleId in pairs(Organization.members) do
    if not table.Contains(Organization.chiefs, roleId) then
      table.insert(datas, roleId);
    end
  end

  table.sort(datas,
    function(a, b)
      if Organization.members[a].online and not Organization.members[b].online then
        return true;
      elseif not Organization.members[a].online and Organization.members[b].online ~= nil then
        return false;
      elseif Organization.IsChief(a) and not Organization.IsChief(b) then
        return true;
      elseif not Organization.IsChief(a) and Organization.IsChief(b) then
        return false;
      elseif Organization.IsSubChief(a) and not Organization.IsSubChief(b) then
        return true;
      elseif not Organization.IsSubChief(a) and Organization.IsSubChief(b) then
        return false;
      elseif Organization.members[a].score > Organization.members[b].score then
        return true;
      elseif Organization.members[a].score < Organization.members[b].score then
        return false;
      else
        return a < b;
      end
    end
  )
  
  if reset then
    scrollContent_Member:Reset(table.maxn(datas));
  else
    scrollContent_Member:Refresh(table.maxn(datas));
  end

  --軍團名單
  if currentDuty == EOrgDuty.None then
    gameObjects.InfoScore:SetActive(true);
    gameObjects.InfoWeekScore:SetActive(true);
    gameObjects.SelectInfo:SetActive(false);

    image_MemberBG.rectTransform.sizeDelta = Vector2.New(723, 365);

    text_SelectInfo.text = "";
    text_InfoDuty.text = "";

  --權限設定
  else
    gameObjects.InfoScore:SetActive(false);
    gameObjects.InfoWeekScore:SetActive(false);
    gameObjects.SelectInfo:SetActive(true);
  
    image_MemberBG.rectTransform.sizeDelta = Vector2.New(723, 320);

    --軍團戰權限
    if currentDuty == EOrgDuty.OrgWar then
      local selectCount = 0;
      for roleId in pairs(Organization.members) do
        if Organization.GetDutyFlag(currentDuty, roleId) then
          selectCount = selectCount + 1;
        end
      end
      
      text_SelectInfo.text = string.Concat(selectCount, "/", Activity.orgWarMaxMember);
      text_SelectAll.text = string.Get(21796);
      text_InfoDuty.text = string.Get(21794);

      event_SelectClear.gameObject:SetActive(true);

    --部隊權限
    else
      text_SelectInfo.text = "";
      text_SelectAll.text = string.Get(21506);
      text_InfoDuty.text = string.Get(22035);

      event_SelectClear.gameObject:SetActive(false);
    end
  end
end

function UIArmy.UpdateEnsign()
  if not Contains(currentPage, EUIArmyPage.Ensign) then return end
  
  image_SelectPixel.rectTransform.anchoredPosition = Vector2.New((7 + (pixelScale * 0.5)) + (pixelScale * currentX), -((7 + (pixelScale * 0.5)) + (pixelScale * currentY)));

  local colorIndex = 15;
  local pixelIndex = math.floor((currentX * 24 + currentY) / 2) + 1;

  if ensignData[pixelIndex] ~= nil then
    if (currentX * 24 + currentY) % 2 == 0 then
      colorIndex = bit.band(bit.rshift(ensignData[pixelIndex], 4), 15);
    else
      colorIndex = bit.band(ensignData[pixelIndex], 15);
    end
  end

  image_SelectColor.transform.localPosition = event_Colors[colorIndex].transform.localPosition;
  
  rawImage_Ensign.texture = ensignTexture;
  rawImage_PreviewEnsign.texture = ensignTexture;

  scrollContent_Ensign:Refresh(table.Count(Organization.sampleEnsigns));
end

function UIArmy.UpdateResource(selections, openBag)
  if not Contains(currentPage, EUIArmyPage.Resource) then return end

  if openBag then
    UI.Open(UIBag,
      this.UpdateResource,
      Vector2.New(260, -25),
      false,
      Item.MaxThingGrid,
      this.ArmyFilter
    );
  end

  for i = 1, Organization.maxResource do
    donateResources[i] = 0;
  end
  
  if selections ~= nil then
    for i = 1, Item.MaxThingGrid do
      if selections[i] then
        local itemSave = Item.GetBagItem(EThings.Bag, i);
        for k, v in pairs(Organization.resourceItemMaterial) do
          if Contains(itemDatas[itemSave.Id].material, unpack(v)) then
            donateResources[k] = donateResources[k] + itemDatas[itemSave.Id].level * itemSave.quant;
          end
        end
      end
    end
  end

  if Define.CityEX then
    for i = 1, 5 do
      if donateResources[i] > 0 then
        donateResources[i] = donateResources[i] + math.round(donateResources[i] * City.GetResourceRate(Organization.Id, EOrgResource.Gold));
      end
    end
  end

  this.UpdateDonate();
end

function UIArmy.UpdateProduction()
  if not Contains(currentPage, EUIArmyPage.Production) then return end

  for k, kind in pairs(EOrgProduction) do
    local production = productions[kind];
    local productionState = Organization.productions[kind];

    --製造中
    if productionState.unit ~= 0 then
      currentProductions[kind] = productionState.unit;
    end

    if currentProductions[kind] == nil then
      currentProductions[kind] = 1;
    end

    local productionData = armyProductionDatas[kind][currentProductions[kind]];

    TextureManager.SetPng(ETextureUseType.UI, string.Concat("icon_", productionData.npcId), production.rawImage_Unit);
    production.rawImage_Unit:SetNativeSize();
    
    for i = 1, 5 do
      if Organization.resources[i] >= productionData.requires[i] then
        production.text_Requires[i].text = productionData.requires[i];
      else
        production.text_Requires[i].text = string.GetColorText(productionData.requires[i], Color.Red);
      end
    end

    if productionState.unit ~= 0 then
      --製造中
      production.event_Prev.gameObject:SetActive(false);
      production.event_Next.gameObject:SetActive(false);
      production.timerEvent = function()
        if productionState.doneTime ~= nil and DateTime.Compare(CGTimer.serverTime, productionState.doneTime) < 0 then
          production.text_Requires[6].text = string.GetSecondText(math.ceil((productionState.doneTime - CGTimer.serverTime).TotalSeconds), true);
        else
          if CGTimer.ContainsListener(production.timerEvent) then
            CGTimer.RemoveListener(production.timerEvent);
            this.UpdateProduction();
          end
        end
      end

      if productionState.doneTime ~= nil and DateTime.Compare(CGTimer.serverTime, productionState.doneTime) < 0 then
        if not CGTimer.ContainsListener(production.timerEvent) then
          CGTimer.AddListener(production.timerEvent, 30, true);
        end

        production.text_Unit.text = string.Concat(npcDatas[productionData.npcId].name, "\n", string.GetColorText(string.format(string.Get(22052), string.GetProductionText(kind)), Color.Red));
        production.text_Take.text = string.Get(20991);
      else
        production.text_Unit.text = npcDatas[productionData.npcId].name;
        production.text_Requires[6].text = string.Get(21688);
        production.text_Take.text = string.Get(99689);
      end
    else
      production.event_Prev.gameObject:SetActive(true);
      production.event_Next.gameObject:SetActive(true);

      production.text_Unit.text = npcDatas[productionData.npcId].name;
      production.text_Requires[6].text = string.GetSecondText(armyProductionDatas[kind][currentProductions[kind]].requires[6] * 60, true);
      production.text_Take.text = string.GetProductionText(kind);

      if CGTimer.ContainsListener(production.timerEvent) then
        CGTimer.RemoveListener(production.timerEvent);
      end
    end

  end
end

function UIArmy.UpdateDonate()
  text_Donate.text = string.Concat(string.Get(20771), Organization.members[Role.playerId].score);

  local donateMoney = 0;
  if not string.IsNullOrEmpty(inputField_Donate.text) then
    donateMoney = tonumber(inputField_Donate.text);
  end
  
  local donateCount = 0;
  for i = 1, Organization.maxResource do
    text_Resources[i].text = Organization.resources[i];

    if i == 1 and donateMoney > 0 then
      donateCount = donateCount + donateResources[i] + donateMoney;
      text_Resources[i].text = string.Concat(text_Resources[i].text, " + ", (donateResources[i] + donateMoney));
    elseif donateResources[i] > 0 then
      donateCount = donateCount + donateResources[i];
      text_Resources[i].text = string.Concat(text_Resources[i].text, " + ", donateResources[i]);
    end
  end

  donateCount = math.floor(donateCount * 0.1);
  if donateCount > 0 then
    text_Donate.text = string.Concat(text_Donate.text, " + ", donateCount);
  end
end

function UIArmy.UpdateArmy()
  if not Contains(currentPage, EUIArmyPage.Army) then return end

  for i = 1, City.maxArmy do
    TextureManager.SetPng(ETextureUseType.UI, string.Concat("icon_", City.armyNpcId[i]), rawImage_Armys[i]);
    text_Armys[i].text = Organization.armys[i];
  end

  event_ArmyDuty.gameObject:SetActive(Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId));
end

function UIArmy.UpdateWeapon(selections, openBag)
  if not Contains(currentPage, EUIArmyPage.Weapon) then return end

  if openBag then
    UI.Open(UIBag,
      this.UpdateWeapon,
      Vector2.New(260, -25),
      false,
      Item.MaxThingGrid,
      function(itemSave)
        return Contains(itemSave.Id, unpack(City.weaponItemId));
      end
    );
  end

  for i = 1, City.maxWeapon do
    donateWeapons[i] = 0;
  end

  --[[
  if selections ~= nil then
    for i = 1, Item.MaxThingGrid do
      if selections[i] then
        local itemSave = Item.GetBagItem(EThings.Bag, i);

        for j = 1, City.maxWeapon do
          if itemSave.Id == City.weaponItemId[j] then
            donateWeapons[j] = donateWeapons[j] + itemSave.quant;
            break;
          end
        end
      end
    end
  end
  --]]

  local canTakeWeapon = Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId);

  for i = 1, City.maxWeapon do
    TextureManager.SetPng(ETextureUseType.UI, string.Concat("icon_", City.weaponNpcId[i]), weapons[i].rawImage_Weapon);
    weapons[i].text_Weapon.text = Organization.weapons[i];

    if donateWeapons[i] > 0 then
      weapons[i].text_Weapon.text = string.Concat(weapons[i].text_Weapon.text, " + ", donateWeapons[i]);
    end

    weapons[i].event_Weapon.gameObject:SetActive(canTakeWeapon);
  end
end

function UIArmy.UpdateBoss(reset)
  if not UI.IsVisible(this) then return end

  if not Contains(currentPage, EUIArmyPage.Boss) then return end

  RedDot.Remove(ERedDot.OrgBoss);

  TextureManager.SetPng(ETextureUseType.UI, "common0_bod120", rawImage_Boss[1]);
  TextureManager.SetPng(ETextureUseType.UI, "common0_bod113", rawImage_Boss[2]);

  text_BossLv.text = Organization.bossCount;
  text_BossTimes.text = string.Concat(RoleCount.Get(ERoleCount.OrgBoss), " / ", Organization.bossFightMaxTimes);

  local bossMaxHp = Organization.GetBossHp();
  local bossHp = math.max(bossMaxHp - Organization.bossDamage, 0);

  text_BossHp.text = string.Concat(bossHp, " / ", bossMaxHp);
  slider_BossHp.maxValue = bossMaxHp;
  slider_BossHp.value = bossHp;
  
  for i = 1, 5 do
    if orgBossAwardDatas[i] ~= nil then
      item_BossAward[i]:SetItem(orgBossAwardDatas[i].itemId, orgBossAwardDatas[i].count);
    else
      item_BossAward[i]:SetItem(0);
    end
  end

  table.Clear(datas);
  for roleId in pairs(Organization.members) do
    table.insert(datas, roleId);
  end

  table.sort(datas,
    function(a, b)
      if Organization.members[a].bossDamage > Organization.members[b].bossDamage then
        return true;
      elseif Organization.members[a].bossDamage < Organization.members[b].bossDamage then
        return false;
      else
        return a < b;
      end
    end
  );

  if reset then
    scrollContent_BossRank:Reset(table.maxn(datas));
  else
    scrollContent_BossRank:Refresh(table.maxn(datas));
  end

  if not CGTimer.ContainsListener(this.UpdateBossTime) then
    CGTimer.AddListener(this.UpdateBossTime, 1, true);
  end
end

function UIArmy.UpdateBossTime()
  if not Contains(currentPage, EUIArmyPage.Boss) then
    CGTimer.RemoveListener(this.UpdateBossTime);
    return;
  end

  local canFihgt = RoleCount.Get(ERoleCount.OrgBoss) < Organization.bossFightMaxTimes;

  if Organization.bossCooldownTime ~= nil and DateTime.Compare(CGTimer.serverTime, Organization.bossCooldownTime) < 0 then
    text_BossCooldown.gameObject:SetActive(true);
    text_BossCooldown.text = string.GetSecondText(math.ceil((Organization.bossCooldownTime - CGTimer.serverTime).TotalSeconds));
    canFihgt = false;
  else
    text_BossCooldown.gameObject:SetActive(false);
    text_BossCooldown.text = "";

    if CGTimer.ContainsListener(this.UpdateBossTime) then
      CGTimer.RemoveListener(this.UpdateBossTime);
    end
  end

  if canFihgt then
    event_BossFight:SetColor(Color.White);
  else
    event_BossFight:SetColor(Color.LightGray);
  end
end

function UIArmy.UpdateAlly(reset)
  if not Contains(currentPage, EUIArmyPage.Ally) then return end

  if Organization.orgs[Organization.Id].allyId ~= 0 then
    image_AllySwitch.sprite = UI.GetSwitchSprite(true);
  else
    image_AllySwitch.sprite = UI.GetSwitchSprite(Organization.allySwitch);
  end

  table.Clear(datas);

  for orgId in pairs(Organization.orgs) do
    if orgId ~= Organization.Id then
      table.insert(datas, orgId);
    end
  end

  table.sort(
    datas,
    function(a, b)
      if Organization.orgs[Organization.Id].allyId == 0 then
        return Organization.orgs[a].memberCount > Organization.orgs[b].memberCount;
      elseif Organization.orgs[a].allyId == Organization.orgs[Organization.Id].allyId and Organization.orgs[b].allyId ~= Organization.orgs[Organization.Id].allyId then
        return true;
      elseif Organization.orgs[a].allyId ~= Organization.orgs[Organization.Id].allyId and Organization.orgs[b].allyId == Organization.orgs[Organization.Id].allyId then
        return false;
      else
        return Organization.orgs[a].memberCount > Organization.orgs[b].memberCount;
      end
    end
  );

  if reset then
    scrollContent_Ally:Reset(table.maxn(datas));
  else
    scrollContent_Ally:Refresh(table.maxn(datas));
  end
end

function UIArmy.UpdateFunction()
  event_FunctionBG.gameObject:SetActive(currentFunction ~= EUIArmyFunction.None);
  gameObjects.Declaration:SetActive(currentFunction == EUIArmyFunction.Declaration);
  gameObjects.Sign:SetActive(currentFunction == EUIArmyFunction.Sign);

  if currentFunction == EUIArmyFunction.Declaration then
    image_FunctionContentBG.rectTransform.sizeDelta = Vector2.New(410, 320);
    infoTexts.text_Function.text = string.Get(10199);

  elseif currentFunction == EUIArmyFunction.Sign then
    image_FunctionContentBG.rectTransform.sizeDelta = Vector2.New(650, 400);
    infoTexts.text_Function.text = string.Get(10200);

    image_Signed.gameObject:SetActive(BitFlag.Get(14));

    local signLv = Organization.GetSignLv();
    local text = "";

    if armySignDatas[signLv + 1] ~= nil then
      infoTexts.text_NextSignLv.text = string.Get(armySignDatas[signLv + 1].title);

      text = "";
      if Organization.level < armySignDatas[signLv + 1].requireArmyLv then
        text = string.Concat(text, string.GetColorText(string.Concat(string.Get(21123), " ", armySignDatas[signLv + 1].requireArmyLv), Color.Red), "\n");
      else
        text = string.Concat(text, string.Get(21123), " ", armySignDatas[signLv + 1].requireArmyLv, "\n");
      end

      if Organization.members[Role.playerId].score < armySignDatas[signLv + 1].requireScore then
        text = string.Concat(text, string.GetColorText(string.Concat(string.Get(10166), " ", armySignDatas[signLv + 1].requireScore), Color.Red), "\n");
      else
        text = string.Concat(text, string.Get(10166), " ", armySignDatas[signLv + 1].requireScore, "\n");
      end
      
      if not Role.CheckPlayerLv(armySignDatas[signLv + 1].requireLv) then
        text = string.Concat(text, string.GetColorText(string.Concat(string.Get(20031), " ", armySignDatas[signLv + 1].requireLv), Color.Red));
      else
        text = string.Concat(text, string.Get(20031), " ", armySignDatas[signLv + 1].requireLv);
      end

      infoTexts.text_NextSignRequire1.text = text;

      text = "";
      for i = 1, 3 do
        if itemDatas[armySignDatas[signLv + 1].awards[i].itemId] ~= nil then
          text = string.Concat(text, itemDatas[armySignDatas[signLv + 1].awards[i].itemId]:GetName(true, true), "x ", armySignDatas[signLv + 1].awards[i].count);

          if i < 3 then
            text = string.Concat(text, "\n");
          end
        end
      end

      infoTexts.text_NextSignRequire2.text = text;

      text = "";
      for i = 4, 5 do
        if itemDatas[armySignDatas[signLv + 1].awards[i].itemId] ~= nil then
          text = string.Concat(text, itemDatas[armySignDatas[signLv + 1].awards[i].itemId]:GetName(true, true), "x ", armySignDatas[signLv + 1].awards[i].count);

          if i < 5 then
            text = string.Concat(text, "\n");
          end
        end
      end

      infoTexts.text_NextSignRequire3.text = text;
    else
      infoTexts.text_NextSignLv.text = "";
      infoTexts.text_NextSignRequire1.text = "";
      infoTexts.text_NextSignRequire2.text = "";
      infoTexts.text_NextSignRequire3.text = "";
    end

    if armySignDatas[signLv] ~= nil then
      infoTexts.text_CurrentSignLv.text = string.Get(armySignDatas[signLv].title);

      text = "";
      text = string.Concat(text, string.Get(21123), " ", armySignDatas[signLv].requireArmyLv, "\n");
      text = string.Concat(text, string.Get(10166), " ", armySignDatas[signLv].requireScore, "\n");
      text = string.Concat(text, string.Get(20031), " ", armySignDatas[signLv].requireLv);

      infoTexts.text_CurrentSignRequire1.text = text;

      text = "";
      for i = 1, 3 do
        if itemDatas[armySignDatas[signLv].awards[i].itemId] ~= nil then
          text = string.Concat(text, itemDatas[armySignDatas[signLv].awards[i].itemId]:GetName(true, true), "x ", armySignDatas[signLv].awards[i].count);
          
          if i < 3 then
            text = string.Concat(text, "\n");
          end
        end
      end

      infoTexts.text_CurrentSignRequire2.text = text;

      text = "";
      for i = 4, 5 do
        if itemDatas[armySignDatas[signLv].awards[i].itemId] ~= nil then
          text = string.Concat(text, itemDatas[armySignDatas[signLv].awards[i].itemId]:GetName(true, true), "x ", armySignDatas[signLv].awards[i].count);
          
          if i < 5 then
            text = string.Concat(text, "\n");
          end
        end
      end

      infoTexts.text_CurrentSignRequire3.text = text;
    else
      infoTexts.text_CurrentSignLv.text = "";
      infoTexts.text_CurrentSignRequire1.text = "";
      infoTexts.text_CurrentSignRequire2.text = "";
      infoTexts.text_CurrentSignRequire3.text = "";
    end
  end
end

function UIArmy.UpdateSupportPoke()
  if not Contains(currentPage, EUIArmyPage.SupportPoke) then return end
  text_PokeCount.text = string.format(string.Get(24433), RoleCount.Get(ERoleCount.ArmySupportPoke));

  RedDot.Remove(ERedDot.ArmySupportPoke);

  for i = 1, ArmySupport.pokeMaxCount do
    if ArmySupport.IsSpecial(i) then
      pokeGroup[i].image_BG.sprite = UI.GetSprite("bod046");
      pokeGroup[i].image_BG.color = LuaHelper.GetColor(255, 201, 0, 255);
    else
      pokeGroup[i].image_BG.sprite = UI.GetSprite("bod035");
      pokeGroup[i].image_BG.color = Color.White;
    end

    pokeGroup[i].image_Poke.enabled = ArmySupport.pokeData[i].status == 1;

    if ArmySupport.pokeData[i].status == 1 then
      pokeGroup[i].image_Poke.sprite = ArmySupport.PokeImage(ArmySupport.pokedIdx ~= i, ArmySupport.pokeData[i].awardKind == 2);
    end
  end

  if ArmySupport.awardId ~= 0 then
    local dataTemp = armyPokeDatas[ArmySupport.awardId];
    local quantPlus = ArmySupport.GetQuantPlus(ArmySupport.pokedIdx);
    local times, plus = ArmySupport.GetAwardTimesPlus(quantPlus);

    local quant = dataTemp.count * times + plus;

    text_GetAward.text = string.Concat(ArmySupport.GetQuantPlusText(quantPlus),itemDatas[dataTemp.itemId].name, " * ", quant);
    ArmySupport.awardId = 0;
  else
    text_GetAward.text = "----";
  end

  if not CGTimer.ContainsListener(this.UpdateSupportTime) then
    CGTimer.AddListener(this.UpdateSupportTime, 1, true);
  end

  this.UpdateAwardHistory();
end

function UIArmy.UpdateAwardHistory(reset)
  supportData = ArmySupport.GetHistory();

  if reset then
    scrollContent_Message:Reset(table.Count(supportData));
  else
    scrollContent_Message:Refresh(table.Count(supportData));
  end
end

function UIArmy.UpdateSupportInfo(reset)
  if not Contains(currentPage, EUIArmyPage.SupportInfo) then return end

  scoreData = ArmySupport.GetArmyScores();
  rankData = ArmySupport.GetRank();

  local lastIndex = table.Count(scoreData);

  text_TotalScore.text = string.Concat(ArmySupport.armyTotalScore, " / ", scoreData[lastIndex].day);
  slider_ArmyScore.maxValue = scoreData[lastIndex].day;
  slider_ArmyScore.value = ArmySupport.armyTotalScore;

  for i = 1, table.Count(scoreData) do
    if ArmySupport.armyTotalScore >= scoreData[i].day then
      if BitFlag.Get(scoreData[i].flag) then
        supportCanClick = false;
      else
        supportCanClick = true;
        break;
      end
    else
      RedDot.Remove(ERedDot.ArmySupportInfo);
      supportCanClick = false;
    end
  end

  if supportCanClick then
    image_Receive.sprite = UI.GetSprite("but001D");
  else
    image_Receive.sprite = UI.GetSprite("but001L");
  end

  if reset then
    scrollContent_SupportRank:Reset(table.Count(rankData));
    scrollContent_SupportAwards:Reset(table.Count(scoreData));
  else
    scrollContent_SupportRank:Refresh(table.Count(rankData));
    scrollContent_SupportAwards:Refresh(table.Count(scoreData));
  end

  if not CGTimer.ContainsListener(this.UpdateSupportTime) then
    CGTimer.AddListener(this.UpdateSupportTime, 1, true);
  end
end

function UIArmy.UpdateSupportMission(reset)
  if not Contains(currentPage, EUIArmyPage.SupportMission) then return end

  text_SelfScore.text = string.format(string.Get(24427), ArmySupport.playerScore);

  if reset then
    scrollContent_MissionAward:Reset(table.Count(armyMissionAwardDatas));
  else
    scrollContent_MissionAward:Refresh(table.Count(armyMissionAwardDatas));
  end

  if not CGTimer.ContainsListener(this.UpdateSupportMissTime) then
    CGTimer.AddListener(this.UpdateSupportMissTime, 1, true);
  end
end

function UIArmy.UpdateSupportTime()
  if not Contains(currentPage, EUIArmyPage.SupportInfo,EUIArmyPage.SupportPoke) then
    if CGTimer.ContainsListener(this.UpdateSupportTime) then
      CGTimer.RemoveListener(this.UpdateSupportTime);
    end

    return;
  end

  local leftTimeText = "";
  local isAct = true;

  if ArmySupport.ActivityIsOpen(ArmySupport.supportActID) then
    local leftTime = DateTime.Subtract(activityTimes[ArmySupport.supportActID][1].serverCloseTime, CGTimer.serverTime);
    leftTimeText = string.Concat(string.Get(80098), string.GetSecondText(math.ceil(leftTime.TotalSeconds)));
  else
    leftTimeText = string.Get(20377);
    isAct = false;
  end

  text_supportTime.text = leftTimeText;
  text_pokeTime.text = leftTimeText;

  if not isAct and CGTimer.ContainsListener(this.UpdateSupportTime) then
    CGTimer.RemoveListener(this.UpdateSupportTime);
  end
end

function UIArmy.UpdateSupportMissTime()
  if not Contains(currentPage, EUIArmyPage.SupportMission) then
    if CGTimer.ContainsListener(this.UpdateSupportMissTime) then
      CGTimer.RemoveListener(this.UpdateSupportMissTime);
    end

    return;
  end

  if ArmySupport.ActivityIsOpen(ArmySupport.supportMissID) then
    local leftTime = DateTime.Subtract(activityTimes[ArmySupport.supportMissID][1].serverCloseTime, CGTimer.serverTime);
    text_MissionTime.text = string.Concat(string.Get(80098), string.GetSecondText(math.ceil(leftTime.TotalSeconds)));
  else
    text_MissionTime.text = string.Get(20377);

    if  CGTimer.ContainsListener(this.UpdateSupportMissTime) then
      CGTimer.RemoveListener(this.UpdateSupportMissTime);
    end
  end
end
  --endregion

  --region  ui
function UIArmy.OnClick_Close()
  UI.Close(UIArmy);

  return true;
end

function UIArmy.OnClick_Help(checkShow)
  if checkShow ~= true then
    if currentPage == EUIArmyPage.Boss then
      UI.Open(UITutorialHint, 0, this, string.Get(99790), string.Get(99791));
    elseif Contains( currentPage, EUIArmyPage.SupportInfo, EUIArmyPage.SupportMission, EUIArmyPage.SupportPoke) then
      UI.Open(UITutorialHint, 0, this, string.Get(24404), string.Get(24437));
    else
      UI.Open(UITutorialHint, 21, this);
    end
  end

  return true;
end

function UIArmy.OnClick_Tag(mainTag, subTag)
  if mainTag == pageTagInfos[EUIArmyPage.Invite][1] and subTag == pageTagInfos[EUIArmyPage.Invite][2] then
    UI.Open(UIInvitation, true, EInvitation.OrgMember);
    return false;
  end

  for k, v in pairs(pageTagInfos) do
    if v[1] == mainTag and v[2] == subTag then
      currentPage = k;
      break;
    end
  end

  currentDuty = EOrgDuty.None;

  if currentPage == EUIArmyPage.SupportInfo then
    --C:039-160 <問支援戰軍團資料>
    Network.Send(39, 160);
  elseif currentPage == EUIArmyPage.SupportPoke then
    --C: 039-163 <問洞洞樂資料>
    Network.Send(39, 163);
    --C: 039-164 <領取洞洞樂次數>
    Network.Send(39, 164);
  end

  this.UpdateUI(true);

  return true;
end
  --endregion

  --region Info
function UIArmy.OnClick_Declaration()
  currentFunction = EUIArmyFunction.Declaration;

  this.UpdateFunction();
end

function UIArmy.OnClick_Sign()
  currentFunction = EUIArmyFunction.Sign;

  this.UpdateFunction();
end

function UIArmy.OnClick_WeekAwardSwitch(uiEvent)
  this.UpdateWeekAward(uiEvent.parameter);
end

function UIArmy.OnClick_CloseFunction()
  this.CloseSignUI();
end

function UIArmy.CloseSignUI()
  currentFunction = EUIArmyFunction.None;

  this.UpdateFunction();
end

function UIArmy.OnChange_Declaration(text)
  local convertedString = LuaHelper.StringToBytes(DataManager.encoding_Big5, inputField_Declaration.text);
  if string.len(convertedString) > 200 then
    convertedString = string.sub(convertedString, 1, 200);
    inputField_Declaration.text = LuaHelper.BytesToString(DataManager.encoding_Big5, convertedString);
  end
end

function UIArmy.OnClick_DeclarationSubmit()
  if not Organization.IsChief(Role.playerId) then
    ShowCenterMessage(string.Get(40186));
    return;
  end

  local message = inputField_Declaration.text;
  for k, v in pairs(tabooDatas) do
    if string.match(message, string.GetNocase(v)) ~= nil then
      ShowCenterMessage(string.Get(20541));
      return;
    end
  end

  --C:039-009 <設定軍團規章> +軍團規章(?)
  sendBuffer:Clear();
  sendBuffer:WriteString(inputField_Declaration.text);
  Network.Send(39, 9, sendBuffer);

  this.OnClick_CloseFunction();
end

function UIArmy.OnClick_SignSubmit()
  if BitFlag.Get(14) then
    ShowCenterMessage(string.Get(21157));
    return;
  end

  --C:039-105 <每日簽到>
  Network.Send(39, 105);
end

function UIArmy.OnClick_Leaderboard()
  UI.Open(UILeaderboard, 14);
end

function UIArmy.OnClick_Invite()
  if not Organization.IsChief(Role.playerId) and not Organization.IsSubChief(Role.playerId) then
    ShowCenterMessage(string.Get(40186));
    return;
  end
  
  ShowNameInput(string.Get(20502),
    function(name)
      if string.IsNullOrEmpty(name) then return end
      
      Organization.Invite(0, name);
    end
  );
end

function UIArmy.OnClick_Quit()
  if Organization.IsChief(Role.playerId) then
    if table.Count(Organization.members) > 1 then
      ShowNotifyMessage(nil, string.Get(10257));
    else
      ShowCheckMessage(
        function(result)
          if result then
            --C:039-008 <解散軍團>
            Network.Send(39, 8);
          end
        end,
        string.Get(40170),
        Role.player
      );
    end
  else
    local text = "";

    if ArmySupport.ActivityIsOpen(ArmySupport.supportActID) then
      text = string.Get(24428);
    else
      text = string.Get(10253);
    end

    ShowCheckMessage(
      function(result)
        if result then
          --C:039-006 <離開軍團>
          Network.Send(39, 6);
        end
      end,
      text,
      Role.player
    );
  end
end
  --endregion

  --region Member
function UIArmy.OnClick_Member(uiEvent)
  local playerInfo = uiEvent.parameter;

  if UI.IsVisible(UIArmyBattle) then return end

  UI.Open(UISocialFunction, playerInfo, ConvertPositionToUIScene(this.uiController.transform, uiEvent.eventData.position, this.uiController.canvas.worldCamera), true);
end

function UIArmy.OnClick_SelectOne(uiEvent)
  if currentDuty == EOrgDuty.None then return end

  local roleId = uiEvent.parameter;

  --C:039-016 <設定軍團權限> +權限索引(1) +玩家ID(8) +權限開關(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(currentDuty);
  sendBuffer:WriteInt64(roleId);
  sendBuffer:WriteBoolean(not Organization.GetDutyFlag(currentDuty, roleId));
  Network.Send(39, 16, sendBuffer);
end

function UIArmy.OnClick_SelectAll(uiEvent)
  if currentDuty == EOrgDuty.None then return end

  local maxMember = 0;
  if Between(currentDuty, EOrgDuty.WalkerArmy, EOrgDuty.StrongOfficer) then
    currentPage = EUIArmyPage.Army;
    currentDuty = uiEvent.parameter;
  
    this.UpdateUI(true);
    return;
  elseif currentDuty == EOrgDuty.OrgWar then
    --軍團戰
    maxMember = Activity.orgWarMaxMember;
  end

  --全選
  if uiEvent.parameter then
    local count = 0;
    for k, roleId in pairs(datas) do
      if maxMember > 0 and count >= maxMember then
        break;
      end

      if not Organization.GetDutyFlag(currentDuty, roleId) then
        --C:039-016 <設定軍團權限> +權限索引(1) +玩家ID(8) +權限開關(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(currentDuty);
        sendBuffer:WriteInt64(roleId);
        sendBuffer:WriteBoolean(true);
        Network.Send(39, 16, sendBuffer);
      end

      count = count + 1;
    end

  --全取消
  else
    for k, roleId in pairs(datas) do
      if Organization.GetDutyFlag(currentDuty, roleId) then
        --C:039-016 <設定軍團權限> +權限索引(1) +玩家ID(8) +權限開關(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(currentDuty);
        sendBuffer:WriteInt64(roleId);
        sendBuffer:WriteBoolean(false);
        Network.Send(39, 16, sendBuffer);
      end
    end
  end
end

this.OnDropChange_ArmyDuty = {};
function UIArmy.OnClick_SelectArmy(itemIndex, value)
  if scrollItems_Member[itemIndex] == nil then return end
  if scrollItems_Member[itemIndex].event_BG.parameter == nil then return end
  
  local roleId = scrollItems_Member[itemIndex].event_BG.parameter.roleId;
  
  if value > 0 then
    local duty = value + (EOrgDuty.WalkerArmy - 1);
    if not Organization.GetDutyFlag(duty, roleId) then
      --C:039-016 <設定軍團權限> +權限索引(1) +玩家ID(8) +權限開關(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(duty);
      sendBuffer:WriteInt64(roleId);
      sendBuffer:WriteBoolean(true);
      Network.Send(39, 16, sendBuffer);
    end
  else
    for i = 1, City.maxArmy do
      local duty = i + (EOrgDuty.WalkerArmy - 1);
      if Organization.GetDutyFlag(duty, roleId) then
        --C:039-016 <設定軍團權限> +權限索引(1) +玩家ID(8) +權限開關(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(duty);
        sendBuffer:WriteInt64(roleId);
        sendBuffer:WriteBoolean(false);
        Network.Send(39, 16, sendBuffer);
      end
    end
  end
end

function UIArmy.OnInitialize_ScrollContent_Member(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Member[itemIndex] = {};
    scrollItems_Member[itemIndex].event_BG = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_Member[itemIndex].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_Member);

    scrollItems_Member[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_Member[itemIndex].text_Order = scrollItems[itemIndex]:Find("Text_Order"):GetComponent("Text");
    scrollItems_Member[itemIndex].text_Lv = scrollItems[itemIndex]:Find("Text_Level"):GetComponent("Text");
    scrollItems_Member[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("Career"));
    scrollItems_Member[itemIndex].text_Score = scrollItems[itemIndex]:Find("Text_Score"):GetComponent("Text");
    scrollItems_Member[itemIndex].text_WeekScore = scrollItems[itemIndex]:Find("Text_WeekScore"):GetComponent("Text");
    scrollItems_Member[itemIndex].image_Standby = scrollItems[itemIndex]:Find("Image_Standby"):GetComponent("Image");

    scrollItems_Member[itemIndex].evnet_Standby = scrollItems[itemIndex]:Find("Image_Standby"):GetComponent("UIEvent");
    scrollItems_Member[itemIndex].evnet_Standby:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectOne);

    scrollItems_Member[itemIndex].dropdown_Army = scrollItems[itemIndex]:Find("Dropdown_Army"):GetComponent("UIDropdown");
    scrollItems_Member[itemIndex].dropdown_Army:AddOption(string.Get(40400));
    for i = 1, City.maxArmy do
      scrollItems_Member[itemIndex].dropdown_Army:AddOption(npcDatas[City.armyNpcId[i]].name);
    end
    if this.OnDropChange_ArmyDuty[itemIndex] == nil then
      this.OnDropChange_ArmyDuty[itemIndex] = function(value)
        this.OnClick_SelectArmy(itemIndex, value);
      end
    end
    scrollItems_Member[itemIndex].dropdown_Army:AddListener(this.OnDropChange_ArmyDuty[itemIndex]);

    scrollItems_Member[itemIndex].text_State = scrollItems[itemIndex]:Find("Text_State"):GetComponent("Text");
  end
end

function UIArmy.OnItemChange_ScrollContent_Member(dataIndex, itemIndex)
  local roleId = datas[dataIndex + 1];

  if roleId == nil then return false end

  local playerInfo = Organization.members[roleId];

  if playerInfo == nil then return false end

  scrollItems_Member[itemIndex].event_BG.parameter = playerInfo;

  if Between(currentDuty, EOrgDuty.WalkerArmy, EOrgDuty.StrongOfficer) then
    scrollItems_Member[itemIndex].text_Score.text = "";
    scrollItems_Member[itemIndex].text_WeekScore.text = "";
    scrollItems_Member[itemIndex].image_Standby.gameObject:SetActive(false);
    scrollItems_Member[itemIndex].dropdown_Army.gameObject:SetActive(true);
    local dropdownIndex = 0;
    for i = 1, City.maxArmy do
      if Organization.GetDutyFlag(i + (EOrgDuty.WalkerArmy - 1), roleId) then
        dropdownIndex = i;
        break;
      end
    end
    scrollItems_Member[itemIndex].dropdown_Army.value = dropdownIndex;

  elseif Contains(currentDuty, EOrgDuty.OrgWar) then
    scrollItems_Member[itemIndex].text_Score.text = "";
    scrollItems_Member[itemIndex].text_WeekScore.text = "";
    scrollItems_Member[itemIndex].image_Standby.gameObject:SetActive(true);
    scrollItems_Member[itemIndex].image_Standby.sprite = UI.GetCheckBoxSprite(Organization.GetDutyFlag(currentDuty, roleId));
    scrollItems_Member[itemIndex].evnet_Standby.parameter = roleId;
    scrollItems_Member[itemIndex].dropdown_Army.gameObject:SetActive(false);
  else
    scrollItems_Member[itemIndex].text_Score.text = playerInfo.score;
    scrollItems_Member[itemIndex].text_WeekScore.text = playerInfo.weekScore;
    scrollItems_Member[itemIndex].image_Standby.gameObject:SetActive(false);
    scrollItems_Member[itemIndex].dropdown_Army.gameObject:SetActive(false);
  end

  if playerInfo.online then
    scrollItems_Member[itemIndex].text_State.text = string.Get(70003);
  else
    scrollItems_Member[itemIndex].text_State.text = string.Get(20742);
  end

  scrollItems_Member[itemIndex].text_Lv.text = playerInfo.level;

  scrollItems_Member[itemIndex].uiCareer:SetCareer(playerInfo.turn, playerInfo.element, playerInfo.career, false, playerInfo.turn3Info.element);

  scrollItems_Member[itemIndex].text_Name.text = playerInfo.name;

  if Organization.IsChief(roleId) then
    scrollItems_Member[itemIndex].text_Order.text = string.Get(10273);
  elseif Organization.IsSubChief(roleId) then
    scrollItems_Member[itemIndex].text_Order.text = string.Get(10274);
  else
    if playerInfo.score > Organization.orderScore_Elite then
      scrollItems_Member[itemIndex].text_Order.text = string.Get(10275);
    elseif playerInfo.score > Organization.orderScore_Senior then
      scrollItems_Member[itemIndex].text_Order.text = string.Get(10276);
    else
      scrollItems_Member[itemIndex].text_Order.text = string.Get(10277);
    end
  end

  return true;
end
  --endregion

  --region Ensign
function UIArmy.OnClick_Ensign(uiEvent)
  local localPosition = ConvertPositionToUIScene(rawImage_Ensign.transform, uiEvent.eventData.position, this.uiController.canvas.worldCamera);
  
  currentX = math.floor(math.abs(localPosition.x) / pixelScale);
  currentY = math.floor(math.abs(localPosition.y) / pixelScale);

  currentSample = 0;
  
  this.UpdateEnsign();
end

function UIArmy.OnClick_PixelUp()
  if currentY <= 0 then
    currentY = 23;
  else
    currentY = currentY - 1;
  end

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_PixelRight()
  if currentX >= 23 then
    currentX = 0;
  else
    currentX = currentX + 1;
  end

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_PixelDown()
  if currentY >= 23 then
    currentY = 0;
  else
    currentY = currentY + 1;
  end

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_PixelLeft()
  if currentX <= 0 then
    currentX = 23;
  else
    currentX = currentX - 1;
  end

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_Color(uiEvent)
  local pixelIndex = math.floor((currentX * 24 + currentY) / 2) + 1;
  local colorIndex = ensignData[pixelIndex] or 255;

  if (currentX * 24 + currentY) % 2 == 0 then
    ensignData[pixelIndex] = bit.bor(bit.band(colorIndex, 15), bit.lshift(uiEvent.parameter, 4));
  else
    ensignData[pixelIndex] = bit.bor(bit.band(colorIndex, 240), uiEvent.parameter);
  end

  ensignTexture:SetPixel(currentX, 24 - (currentY + 1), Organization.ensignColor[uiEvent.parameter]);
  ensignTexture:Apply();

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_EnsignSave()
  currentSample = 0;

  Organization.AddCustomEnsign(ensignData);
end

function UIArmy.OnClick_EnsignRemove(uiEvent)
  currentSample = 0;
  Organization.RemoveCustomEnsign(uiEvent.parameter);
end

function UIArmy.OnClick_EnsignSubmit()
  if CGTimer.time < Organization.ensignEditTime then
    ShowCenterMessage(string.Get(20916));
    return;
  end
  
  Organization.ensignEditTime = CGTimer.time + 31;

  --C:039-018 <團徽上傳> +團徽資料(?)
  sendBuffer:Clear();
  for i = 1, Organization.ensignDataSize do
    sendBuffer:WriteByte(ensignData[i]);
  end
  Network.Send(39, 18, sendBuffer);

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_EnsignReset(uiEvent)
  local orgData = Organization.orgs[Organization.Id];

  if uiEvent ~= nil then
    local changed = false;
    for i = 1, Organization.ensignDataSize do
      if ensignData[i] ~= orgData.ensignData[i] then
        changed = true;
        break;
      end
    end

    if not changed then
      ShowCheckMessage(
        function(result)
          if result then
            for i = 1, Organization.ensignDataSize do
              ensignData[i] = 255;
            end

            for x = 0, 23 do
              for y = 0, 23 do
                ensignTexture:SetPixel(x, 24 - (y + 1), Organization.ensignColor[15]);
              end
            end
            ensignTexture:Apply();

            currentX = 0;
            currentY = 0;
          
            this.UpdateEnsign();
          end
        end,
        string.Get(21691)
      );
      return;
    end
  end

  for i = 1, Organization.ensignDataSize do
    ensignData[i] = orgData.ensignData[i];
  end

  ensignTexture:LoadRawTextureData(orgData.ensignTexture:GetRawTextureData());
  ensignTexture:Apply();

  currentX = 0;
  currentY = 0;

  currentSample = 0;

  this.UpdateEnsign();
end

function UIArmy.OnClick_SampleEnsign(uiEvent)
  local sampleIndex = uiEvent.parameter;

  if Organization.sampleEnsigns[sampleIndex] == nil then return end

  for i = 1, Organization.ensignDataSize do
    ensignData[i] = Organization.sampleEnsigns[sampleIndex].data[i];
  end

  ensignTexture:LoadRawTextureData(Organization.sampleEnsigns[sampleIndex].texture:GetRawTextureData());
  ensignTexture:Apply();

  currentX = 0;
  currentY = 0;

  currentSample = sampleIndex;

  this.UpdateEnsign();
end

function UIArmy.OnInitialize_ScrollContent_Ensign(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Ensign[itemIndex] = {};
    scrollItems_Ensign[itemIndex].event_BG = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_Ensign[itemIndex].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_SampleEnsign);
    scrollItems_Ensign[itemIndex].rawImage_Ensign = scrollItems[itemIndex]:Find("RawImage_Ensign"):GetComponent("RawImage");
    scrollItems_Ensign[itemIndex].event_Remove = scrollItems[itemIndex]:Find("Image_Select"):GetComponent("UIEvent");
    scrollItems_Ensign[itemIndex].event_Remove:SetListener(EventTriggerType.PointerClick, this.OnClick_EnsignRemove);
  end
end

function UIArmy.OnItemChange_ScrollContent_Ensign(dataIndex, itemIndex)
  if Organization.sampleEnsigns[dataIndex + 1] == nil then return false end

  scrollItems_Ensign[itemIndex].event_BG.parameter = dataIndex + 1;
  scrollItems_Ensign[itemIndex].rawImage_Ensign.texture = Organization.sampleEnsigns[dataIndex + 1].texture;

  if dataIndex + 1 > Organization.sampleEnsignMax and dataIndex + 1 == currentSample then
    scrollItems_Ensign[itemIndex].event_Remove.gameObject:SetActive(true);
    scrollItems_Ensign[itemIndex].event_Remove.parameter = (dataIndex + 1) - Organization.sampleEnsignMax;
  else
    scrollItems_Ensign[itemIndex].event_Remove.gameObject:SetActive(false);
  end

  return true;
end
  --endregion

  --region Resource
function UIArmy.OnChange_Donate(text)
  this.UpdateDonate();
end

function UIArmy.OnClick_DonateResource()
  local selections = UIBag.GetSelect();

  local count = tonumber(inputField_Donate.text);

  if count == nil then
    count = 0;
  end
  
  for i = 1, Item.MaxThingGrid do
    if selections[i] then
      count = count + 1;
    end
  end

  if count == 0 then return end

  --C:039-015 <捐獻資源> +金錢數(4) <<+背包索引(1)>>
  sendBuffer:Clear();
  if tonumber(inputField_Donate.text) ~= nil then
    sendBuffer:WriteInt32(tonumber(inputField_Donate.text));
  else
    sendBuffer:WriteInt32(0);
  end

  for i = 1, Item.MaxThingGrid do
    if selections[i] then
      sendBuffer:WriteByte(i);
    end
  end
  Network.Send(39, 15, sendBuffer);

  inputField_Donate.text = 0;

  UIBag.ClearSelect();
end
  --endregion

  --region Production
function UIArmy.OnClick_ProductPrev(uiEvent)
  local kind = uiEvent.parameter;

  if armyProductionDatas[kind] == nil then return end
  if Organization.productions[kind] ~= nil and Organization.productions[kind].unit ~= 0 then return end

  currentProductions[kind] = currentProductions[kind] - 1;
  if currentProductions[kind] < 1 then
    currentProductions[kind] = table.maxn(armyProductionDatas[kind]);
  end

  this.UpdateProduction();
end

function UIArmy.OnClick_ProductNext(uiEvent)
  local kind = uiEvent.parameter;

  if armyProductionDatas[kind] == nil then return end
  if Organization.productions[kind] ~= nil and Organization.productions[kind].unit ~= 0 then return end

  currentProductions[kind] = currentProductions[kind] + 1;
  if currentProductions[kind] > table.maxn(armyProductionDatas[kind]) then
    currentProductions[kind] = 1;
  end

  this.UpdateProduction();
end

function UIArmy.OnClick_ProductTake(uiEvent)
  local kind = uiEvent.parameter;

  if armyProductionDatas[kind] == nil then return end
  if Organization.productions[kind] ~= nil and Organization.productions[kind].unit ~= 0 then
    if armyProductionDatas[kind][Organization.productions[kind].unit] == nil then return end

    if Organization.productions[kind].doneTime ~= nil and DateTime.Compare(CGTimer.serverTime, Organization.productions[kind].doneTime) < 0 then
      ShowCheckMessage(
        function(result)
          if result then
            --C:039-111 <軍團製造取消> +製造種類(1)
            sendBuffer:Clear();
            sendBuffer:WriteByte(kind);
            Network.Send(39, 111, sendBuffer);
          end
        end,
        string.format(string.Get(21689), string.GetProductionText(kind), string.GetProductionText(kind)),
        Role.player
      );
    else
      for i = 1, 5 do
        if armyProductionDatas[kind][Organization.productions[kind].unit].requires[i] > 0 and Organization.resources[i] < armyProductionDatas[kind][Organization.productions[kind].unit].requires[i] then
          ShowCheckMessage(
            function(result)
              if result then
                --C:039-111 <軍團製造取消> +製造種類(1)
                sendBuffer:Clear();
                sendBuffer:WriteByte(kind);
                Network.Send(39, 111, sendBuffer);
              end
            end,
            string.format(string.Get(21686), string.GetProductionText(kind)),
            Role.player
          );
          return;
        end
      end

      if kind == EOrgProduction.Weapon then
        if Organization.weapons[Organization.productions[kind].unit] >= 50000 then
          ShowCheckMessage(
            function(result)
              if result then
                --C:039-111 <軍團製造取消> +製造種類(1)
                sendBuffer:Clear();
                sendBuffer:WriteByte(kind);
                Network.Send(39, 111, sendBuffer);
              end
            end,
            string.format(string.Get(21685), string.GetProductionText(kind)),
            Role.player
          );
          return;
        end
      elseif kind == EOrgProduction.Army then
        if Organization.armys[Organization.productions[kind].unit] >= 50000 then
          ShowCheckMessage(
            function(result)
              if result then
                --C:039-111 <軍團製造取消> +製造種類(1)
                sendBuffer:Clear();
                sendBuffer:WriteByte(kind);
                Network.Send(39, 111, sendBuffer);
              end
            end,
            string.format(string.Get(21685), string.GetProductionText(kind)),
            Role.player
          );
          return;
        end
      end

      --C:039-112 <軍團製造完成> +製造種類(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(kind);
      Network.Send(39, 112, sendBuffer);
    end
  else
    if armyProductionDatas[kind][currentProductions[kind]] == nil then return end

    for i = 1, 5 do
      if armyProductionDatas[kind][currentProductions[kind]].requires[i] > 0 and Organization.resources[i] < armyProductionDatas[kind][currentProductions[kind]].requires[i] then
        ShowCenterMessage(string.Get(40247));
        return;
      end
    end

    --C:039-110 <軍團製造開始> +製造種類(1)
    --  製造種類 1:器械 +器械種類(1)
    --  製造種類 2:兵種 +兵種種類(1)
    sendBuffer:Clear();
    sendBuffer:WriteByte(kind);
    sendBuffer:WriteByte(currentProductions[kind]);
    Network.Send(39, 110, sendBuffer);
  end
end
  --endregion

  --region Army & Weapon
function UIArmy.OnClick_NpcInfo(uiEvent)
  local npcId = uiEvent.parameter;
  local npcData = npcDatas[npcId];
  if npcData == nil then return end

  local npcInfo = NpcInfo.New();
  npcInfo.npcId = npcId;
  npcInfo.level = npcData.level;
  npcInfo.turn = npcData.turn;
  npcInfo.element = npcData.element;

  npcInfo.hp = Calc.GetMaxHp(nil, npcData.level, npcData.attributes[4], 0, 0, npcData.turn, 0, 0, true);
  npcInfo.sp = Calc.GetMaxSp(nil, npcData.level, npcData.attributes[5], 0, 0, npcData.turn, 0, 0, true);

  npcInfo.int = npcData.attributes[1];
  npcInfo.atk = npcData.attributes[2];
  npcInfo.def = npcData.attributes[3];
  npcInfo.hpx = npcData.attributes[4];
  npcInfo.spx = npcData.attributes[5];
  npcInfo.agi = npcData.attributes[6];

  UI.Open(UINpcInfo, npcInfo, false);
end

function UIArmy.OnClick_ArmyDuty()
  if not (Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId)) then
    ShowCenterMessage(string.Get(40186));
    return;
  end

  currentPage = EUIArmyPage.Member;
  currentDuty = EOrgDuty.WalkerArmy;

  this.UpdateUI(true);
end

function UIArmy.OnClick_Weapon(uiEvent)
  local index = uiEvent.parameter;
  if Organization.weapons[index] <= 0 then return end
  if not (Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId)) then
    ShowCenterMessage(string.Get(40186));
    return;
  end
  
  if Organization.weapons[index] > 1 then
    ShowCountInput(string.Get(30022), 1, math.min(Organization.weapons[index], 9999),
      function(text)
        if text == nil then return end

        --C:039-052 <取出武器> <<+武器索引(1) +取出數量(2)>>
        sendBuffer:Clear();
        sendBuffer:WriteByte(index);
        sendBuffer:WriteUInt16(tonumber(text));
        Network.Send(39, 52, sendBuffer);
      end
    );
  else
    --C:039-052 <取出武器> <<+武器索引(1) +取出數量(2)>>
    sendBuffer:Clear();
    sendBuffer:WriteByte(index);
    sendBuffer:WriteUInt16(1);
    Network.Send(39, 52, sendBuffer);
  end
end

function UIArmy.OnClick_DonateWeapon()
  local selections = UIBag.GetSelect();

  local count = 0;
  for i = 1, Item.MaxThingGrid do
    if selections[i] then
      count = count + 1;
    end
  end

  if count == 0 then return end

  --C:039-053 <歸還軍備> <<+背包索引(1)>>
  sendBuffer:Clear();
  for i = 1, Item.MaxThingGrid do
    if selections[i] then
      sendBuffer:WriteByte(i);
    end
  end
  Network.Send(39, 53, sendBuffer);

  UIBag.ClearSelect();
end
  --endregion

  --region Boss
function UIArmy.OnClick_BossFight()
  if EventManager.IsRunning() then
    ShowCenterMessage(string.Get(21767));
    return;
  end

  if RoleCount.Get(ERoleCount.OrgBoss) >= Organization.bossFightMaxTimes then
    ShowCenterMessage(string.Get(10329));
    return;
  end
  
  if Organization.bossCooldownTime ~= nil and DateTime.Compare(CGTimer.serverTime, Organization.bossCooldownTime) < 0 then
    UISell.Launch(35);
    return;
  end  
  
  --C:039-119 <軍團BOSS戰入場>
  Network.Send(39, 119);
end

function UIArmy.OnInitialize_ScrollContent_BossRank(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_BossRank[itemIndex] = {};
    scrollItems_BossRank[itemIndex].event_BG = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_BossRank[itemIndex].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_Member);

    scrollItems_BossRank[itemIndex].image_Rank = scrollItems[itemIndex]:Find("Image_Rank"):GetComponent("Image");
    scrollItems_BossRank[itemIndex].text_Rank = scrollItems[itemIndex]:Find("Text_Rank"):GetComponent("Text");
    scrollItems_BossRank[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_BossRank[itemIndex].text_Damage = scrollItems[itemIndex]:Find("Text_Damage"):GetComponent("Text");
  end
end

function UIArmy.OnItemChange_ScrollContent_BossRank(dataIndex, itemIndex)
  local roleId = datas[dataIndex + 1];

  if roleId == nil then return false end

  local playerInfo = Organization.members[roleId];

  if playerInfo == nil then return false end

  local rank = dataIndex + 1;
  if rank <= 3 then
    scrollItems_BossRank[itemIndex].image_Rank.sprite = UI.GetRankSprite(rank);
    scrollItems_BossRank[itemIndex].image_Rank.gameObject:SetActive(true);
    scrollItems_BossRank[itemIndex].text_Rank.text = "";
  else
    scrollItems_BossRank[itemIndex].image_Rank.sprite = nil;
    scrollItems_BossRank[itemIndex].image_Rank.gameObject:SetActive(false);
    scrollItems_BossRank[itemIndex].text_Rank.text = rank;
  end

  scrollItems_BossRank[itemIndex].text_Name.text = playerInfo.name;
  scrollItems_BossRank[itemIndex].text_Damage.text = string.GetCommaValue(playerInfo.bossDamage);

  scrollItems_BossRank[itemIndex].event_BG.parameter = playerInfo;

  return true;
end
  --endregion

  --region Ally
function UIArmy.OnClick_AllySwitch()
  if Organization.orgs[Organization.Id].allyId ~= 0 then
    ShowCheckMessage(
      function(result)
        if result then
          --C:043-003 <離開同盟>
          Network.Send(43, 3);

          --C:039-017 <設定同盟開關> +開關(1)
          sendBuffer:Clear();
          sendBuffer:WriteByte(1);
          Network.Send(39, 17, sendBuffer);
        end
      end,
      string.Get(20876),
      Role.player
    );
  else
    --C:039-017 <設定同盟開關> +開關(1)
    sendBuffer:Clear();
    if Organization.allySwitch then
      sendBuffer:WriteByte(1);
    else
      sendBuffer:WriteByte(0);
    end
    Network.Send(39, 17, sendBuffer);
  end
end

function UIArmy.OnClick_AllyRefresh()
  --C:043-004 <要求所有軍團資訊>
  Network.Send(43, 4);
end

function UIArmy.OnClick_AllyInvite(uiEvent)
  if not Organization.allySwitch then return end

  local orgId = uiEvent.parameter;
  if orgId == nil then return end

  local orgInfo = Organization.orgs[orgId];
  if orgInfo == nil then return end
  if orgInfo.allyId ~= 0 then return end
  
  ShowCheckMessage(
    function(result)
      if result then
        --C:043-001 <邀請締結同盟> +軍團ID(4)
        sendBuffer:Clear();
        sendBuffer:WriteUInt32(orgId);
        Network.Send(43, 1, sendBuffer);
      end
    end,
    string.Concat(string.Get(20874), orgInfo.name, string.Get(20875)),
    Role.player
  );
end

function UIArmy.OnInitialize_ScrollContent_Ally(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Ally[itemIndex] = {};
    scrollItems_Ally[itemIndex].event_BG = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_Ally[itemIndex].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_AllyInvite);
    scrollItems_Ally[itemIndex].rawImage_Ensign = scrollItems[itemIndex]:Find("RawImage_Ensign"):GetComponent("RawImage");
    scrollItems_Ally[itemIndex].text_OrgName = scrollItems[itemIndex]:Find("Text_OrgName"):GetComponent("Text");
    scrollItems_Ally[itemIndex].text_ChiefName = scrollItems[itemIndex]:Find("Text_ChiefName"):GetComponent("Text");
    scrollItems_Ally[itemIndex].text_AllyState = scrollItems[itemIndex]:Find("Text_AllyState"):GetComponent("Text");
    scrollItems_Ally[itemIndex].text_MemberCount = scrollItems[itemIndex]:Find("Text_MemberCount"):GetComponent("Text");
    scrollItems_Ally[itemIndex].text_State = scrollItems[itemIndex]:Find("Text_State"):GetComponent("Text");
  end
end

function UIArmy.OnItemChange_ScrollContent_Ally(dataIndex, itemIndex)
  local orgId = datas[dataIndex + 1];

  if orgId == nil then return false end

  local orgInfo = Organization.orgs[orgId];

  if orgInfo == nil then return false end

  scrollItems_Ally[itemIndex].event_BG.parameter = orgId;

  scrollItems_Ally[itemIndex].rawImage_Ensign.texture = Organization.GetEnsign(orgId);
  scrollItems_Ally[itemIndex].text_OrgName.text = orgInfo.name;
  scrollItems_Ally[itemIndex].text_ChiefName.text = orgInfo.chiefName;

  if orgInfo.allyId == 0 then
    scrollItems_Ally[itemIndex].text_AllyState.text = string.Get(20846);
  elseif orgInfo.allyId == Organization.orgs[Organization.Id].allyId then
    scrollItems_Ally[itemIndex].text_AllyState.text = string.Get(20844);
  else
    scrollItems_Ally[itemIndex].text_AllyState.text = string.Get(20845);
  end

  scrollItems_Ally[itemIndex].text_MemberCount.text = string.Concat(orgInfo.memberCount, string.Get(22027));

  if orgInfo.chiefOnline then
    scrollItems_Ally[itemIndex].text_State.text = string.Get(70003);
  else
    scrollItems_Ally[itemIndex].text_State.text = string.Get(20742);
  end

  return true;
end

function UIArmy.ArmyFilter(itemSave)
  local itemData = itemDatas[itemSave.Id];
  if itemData == nil then return false end
  if itemData.kind == 53 then return true end
  if itemData.level == 0 then return false end
  if itemSave.isVender then return false end
  if itemSave.isDeliver then return false end
  if itemSave.isLock then return false end
  if Contains(itemSave.Id, 10505, 19209, 20209, 21609, 22909, 20747, 20748, 20749, 26209, 26210, 26211, 26212, 26213, 26214, 26215, 26216, 26217, 26218, 26219, 16000, 21610, 19210, 22910, 20210, 11046) then return false end
  if not Contains(itemData.material, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36) then return false end
  if Contains(itemData.kind, 20, 21, 22) then return false end
  
  return true;
end
  --endregion

  --region ArmySupport
function UIArmy.OnInitialize_ScrollContent_MissionAward(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_MissionAward[itemIndex] = {};
    scrollItems_MissionAward[itemIndex].missionContent = scrollItems[itemIndex]:Find("Text_MissionContent"):GetComponent("Text");
    scrollItems_MissionAward[itemIndex].count = scrollItems[itemIndex]:Find("Text_Count"):GetComponent("Text");
    scrollItems_MissionAward[itemIndex].limit = scrollItems[itemIndex]:Find("Text_Limit"):GetComponent("Text");
    scrollItems_MissionAward[itemIndex].score = scrollItems[itemIndex]:Find("Text_Score"):GetComponent("Text");
    scrollItems_MissionAward[itemIndex].itemAward = UIItemController.New(scrollItems[itemIndex]:Find("Image_ItemAward"));
  end
end

function UIArmy.OnItemChange_ScrollContent_MissionAward(dataIndex, itemIndex)
  local dataTemp = armyMissionAwardDatas[dataIndex + 1];

  if dataTemp ~= nil then
    scrollItems_MissionAward[itemIndex].missionContent.text = string.Get(dataTemp.stringId);
    scrollItems_MissionAward[itemIndex].count.text = string.Concat(RoleCount.Get(dataTemp.roleCount),"/",dataTemp.value);
    scrollItems_MissionAward[itemIndex].score.text = math.floor(RoleCount.Get(dataTemp.roleCount) / dataTemp.value) * dataTemp.quant;
    scrollItems_MissionAward[itemIndex].itemAward:SetItem(dataTemp.item, dataTemp.quant);

    if dataTemp.missionLimit > 0 then
      scrollItems_MissionAward[itemIndex].limit.text = string.format(string.Get(24680), dataTemp.missionLimit);
    else
      scrollItems_MissionAward[itemIndex].limit.text = "";
    end

    return true;
  else
    return false;
  end
end

function UIArmy.OnInitialize_ScrollContent_SupportRank(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_SupportRank[itemIndex] = {};
    scrollItems_SupportRank[itemIndex].image_Rank = scrollItems[itemIndex]:Find("Image_Rank"):GetComponent("Image");
    scrollItems_SupportRank[itemIndex].text_Rank = scrollItems[itemIndex]:Find("Text_Rank"):GetComponent("Text");
    scrollItems_SupportRank[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_SupportRank[itemIndex].text_Score = scrollItems[itemIndex]:Find("Text_Score"):GetComponent("Text");
  end
end

function UIArmy.OnItemChange_ScrollContent_SupportRank(dataIndex, itemIndex)
  local dataTemp = rankData[dataIndex + 1];
  
  if dataTemp == nil then return false end
  
  local playerInfo = Organization.members[dataTemp.playerId];
  
  if playerInfo == nil then return false end
  
  local rank = dataIndex + 1;
  if rank <= 3 then
    scrollItems_SupportRank[itemIndex].image_Rank.sprite = UI.GetRankSprite(rank);
    scrollItems_SupportRank[itemIndex].image_Rank.gameObject:SetActive(true);
    scrollItems_SupportRank[itemIndex].text_Rank.text = "";
  else
    scrollItems_SupportRank[itemIndex].image_Rank.sprite = nil;
    scrollItems_SupportRank[itemIndex].image_Rank.gameObject:SetActive(false);
    scrollItems_SupportRank[itemIndex].text_Rank.text = rank;
  end
  
  scrollItems_SupportRank[itemIndex].text_Name.text = playerInfo.name;
  --累積分數
  scrollItems_SupportRank[itemIndex].text_Score.text = dataTemp.score;

  return true;
end

function UIArmy.OnInitialize_ScrollContent_SupportAwards(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_SupportAwards[itemIndex] = {};
    scrollItems_SupportAwards[itemIndex].item = UIItemController.New(scrollItems[itemIndex]);
    scrollItems_SupportAwards[itemIndex].image_AwardOutline = scrollItems[itemIndex]:Find("Image_AwardOutline"):GetComponent("Image");
    scrollItems_SupportAwards[itemIndex].image_Check = scrollItems[itemIndex]:Find("Image_Check"):GetComponent("Image");
    scrollItems_SupportAwards[itemIndex].image_Mask = scrollItems[itemIndex]:Find("Image_Gray"):GetComponent("Image");
    scrollItems_SupportAwards[itemIndex].text_Score = scrollItems[itemIndex]:Find("Text_Award"):GetComponent("Text");
    scrollItems_SupportAwards[itemIndex].event_Award = scrollItems[itemIndex].gameObject:GetComponent("UIEvent");
    scrollItems_SupportAwards[itemIndex].event_Award:SetListener(EventTriggerType.PointerClick, this.OnClick_SupportGetAward);
    scrollItems_SupportAwards[itemIndex].event_Award.parameter = itemIndex + 1;
  end
end

function UIArmy.OnItemChange_ScrollContent_SupportAwards(dataIndex, itemIndex)
  local dataTemp = scoreData[dataIndex + 1];

  if dataTemp == nil then return false end

  scrollItems_SupportAwards[itemIndex].item:SetItem(dataTemp.itemId[1], dataTemp.quant[1]);
  scrollItems_SupportAwards[itemIndex].image_AwardOutline.enabled = ArmySupport.armyTotalScore >= dataTemp.day and not BitFlag.Get(dataTemp.flag);
  scrollItems_SupportAwards[itemIndex].image_Check.enabled = BitFlag.Get(dataTemp.flag);
  scrollItems_SupportAwards[itemIndex].image_Mask.enabled = ArmySupport.armyTotalScore < dataTemp.day or BitFlag.Get(dataTemp.flag);
  scrollItems_SupportAwards[itemIndex].text_Score.text = string.format(string.Get(24409), dataTemp.day);

  return true;
end

function UIArmy.OnInitialize_scrollContent_Message(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Message[itemIndex] = {};
    scrollItems_Message[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_Message[itemIndex].text_Item = scrollItems[itemIndex]:Find("Text_Item"):GetComponent("Text");
    scrollItems_Message[itemIndex].text_Qunat = scrollItems[itemIndex]:Find("Text_Quant"):GetComponent("Text");
  end
end

function UIArmy.OnItemChange_scrollContent_Message(dataIndex, itemIndex)
  local data = supportData[dataIndex + 1];
  local item = armyPokeDatas[data.item];

  if data ~= nil and item ~= nil then
    local times, plus = ArmySupport.GetAwardTimesPlus(data.quantPlus);

    scrollItems_Message[itemIndex].text_Name.text = string.GetColorText(data.name, Color.Red);
    scrollItems_Message[itemIndex].text_Item.text = string.GetColorText(itemDatas[item.itemId].name, Color.VeryLightBlue);
    scrollItems_Message[itemIndex].text_Qunat.text = string.Concat("*", item.count * times + plus, ArmySupport.GetQuantPlusText(data.quantPlus));

    return true;
  else
    return false;
  end
end

function UIArmy.OnClick_SupportGetAward(uiEvent)
  local index = uiEvent.parameter;
  ArmySupport.SendGetArmySupportReward(index);
end

function UIArmy.OnClick_Poke()
  if ArmySupport.pokeTime ~= 0 and (CGTimer.serverTime - ArmySupport.pokeTime).TotalSeconds < 1 then
      ShowCenterMessage(string.Get(22841));
      return;
  else
    ArmySupport.pokeTime = CGTimer.serverTime;
  end

  Network.Send(39, 162);
end

function UIArmy.OnClick_ShowPrice()
  UI.OpenAlwaysUseArgument(UIItemInfo, EThings.ArmyPoke, 65361, 0);
end

function UIArmy.OnClick_Refresh()
  Network.Send(39, 163);
end

function UIArmy.OnClick_Receive()
  if not supportCanClick then return end
  for i = 1, table.Count(scoreData) do
    if ArmySupport.armyTotalScore >= scoreData[i].day and not BitFlag.Get(scoreData[i].flag) then
      sendBuffer:Clear();
      sendBuffer:WriteByte(EAward.ArmySupport);
      sendBuffer:WriteInt32(scoreData[i].day);
      sendBuffer:WriteByte(scoreData[i].quant[1]);
      Network.Send(87, 2, sendBuffer);
    end
  end
end
  --endregion