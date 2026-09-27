EUIStatusTag = {
  Status = 1,
  Moral = 2,
  Equip = 3,
  Style = 4,
  Astrolabe = 5,
  Profile = 6,
  Breakthrough = 7,
  AttSet = 8,
  Sell = 9,
  ChatPaste = 10,
  FightEquip = 11,
  FightStyle = 12,
  OutfitStyle = 13,
}

local EUIStatusGameObject =
{
  Role = 1,
  Bar = 2,
  Status = 3,
  Equip = 4,
  Moral = 5,
  Detail = 6,
  BarValue = 7,
  Astrolabe = 8,
  Breakthrough = 9,
  OutfitStyle = 10,
  AttSet = 11,
}

UIStatus = {}
local this = UIStatus;

this.name = "UIStatus";
this.uiController = nil;

--ui
local transform_FrameRect;

local UIStatusGameObject = {}

local gameObject_UpgradeLvs = {};
local gameObject_UpgradeItem;
local gameObject_UpgradeSkill;

--Role
local uiCareer;

local jmpDrawer_Role;

local text_Name;
local text_Lv;
local image_UpgradeLv;
local text_UpgradeLv;
local image_Prev;
local image_Next;

local text_Hp;
local text_Sp;
local text_Exp;
local text_FullExp;
local text_FullHp;
local text_FullSp;

local slider_Hp;
local slider_Sp;
local slider_Exp;

local event_Share;
local event_ChgName;

--Status
local text_Point;
local text_AttributePoint;
local text_AttributeSubmit;
local text_AttributeReset;
local image_CurrentUpgradeLv;
local text_CurrentUpgradeLv;
local image_NextUpgradeLv;
local text_NextUpgradeLv;
local text_UpgradePoint;
local text_UpgradeMoney;
local text_UpgradeItems = {};
local text_CurrentUpgradeSkill;
local text_NextUpgradeSkill;
local text_BarValue;
local text_AttSetName;

local rawImage_CurrentUpgradeSkill;
local rawImage_NextUpgradeSkill;

local item_Upgrades = {};

local attComponents = {};
local attSetComponents = {};
local editingAttSet = false;

local event_AttributeSubmit;
local event_AttributeReset;

--Breakthrough
local scrollContent_Breakthrough;
local scrollItems_Breakthrough = {};
local text_BreakthroughLevel;

--Moral
local text_Morals = {};
local radar_Moral;

--Equip
local item_Equips = {};
local event_AllUnEquip;

--OutfitStyle
local image_CategoryHead;
local image_CategoryBody;
local image_CategoryWrist;
local image_CategoryFoot;
local image_CategoryHand;
local image_SwitchOutfitStyle;
local switchOutfitStyle;
local text_collectTitle;
local gameObject_styleTagInfo;
local text_styleTagInfo;
local gameObject_switchOutfitStyleButText;
local text_switchOutfitStyleButText;

--Detail
local gameObject_TreasureDetailRoot;

local treasureDetailUI;
local detailUI;
local detailArrow;

local image_OtherDetail;

--Astrolabe
local text_AstrolabePoint;
local text_AstrolabeMoney;
local text_AstrolabeAttribute = {};
local text_AstrolabeNextAttribute = {};

local image_AstrolabeMask;
local rawImage_AstrolabeBG;
local rawImage_AstrolabePoint;
local rawImage_AstrolabeStar = {};
local rawImage_AstrolabeBall = {};

--AttSet
local gameObject_AttSetFunction;
local gameObject_EditAttSetFunction;
local scrollContent_AttSet;
local scrollItems_AttSet = {};
local text_AttSetSumPoint;
local text_AttSetRemainPoint;

--var
local showDetail = false;
local showTreasureDetail = false;
local tagOffset = 0;
local currentTag = EUIStatusTag.Status;
local currentFollowIndex = 0;
local currentRole = nil;
local upgradeNpcId = 0;
local astrolabeShowTimer = 0;
local mainTag;
local attributeFastPoint = 100;
local currentOutfitSyleCategory = 0;
local outfitStyleIdTempSave = 0;
local attSetRemainPoint = 0;
local convertPage = 0;

local tagDatas = {};

function UIStatus.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  this.uiController.onClose = this.OnClose;

  local tempEvent;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  UIStatusGameObject[EUIStatusGameObject.Role] = uiController:FindGameObject("Role");
  UIStatusGameObject[EUIStatusGameObject.Bar] = uiController:FindGameObject("Bar");
  UIStatusGameObject[EUIStatusGameObject.Status] = uiController:FindGameObject("Status");
  UIStatusGameObject[EUIStatusGameObject.Equip] = uiController:FindGameObject("Equip");
  UIStatusGameObject[EUIStatusGameObject.Moral] = uiController:FindGameObject("Moral");
  UIStatusGameObject[EUIStatusGameObject.Detail] = uiController:FindGameObject("Detail");
  UIStatusGameObject[EUIStatusGameObject.BarValue] = uiController:FindGameObject("BarValue");
  UIStatusGameObject[EUIStatusGameObject.Astrolabe] = uiController:FindGameObject("Astrolabe");
  UIStatusGameObject[EUIStatusGameObject.Breakthrough] = uiController:FindGameObject("GameObject_Breakthrough");
  UIStatusGameObject[EUIStatusGameObject.OutfitStyle] = uiController:FindGameObject("OutfitStyle");
  UIStatusGameObject[EUIStatusGameObject.AttSet] = uiController:FindGameObject("AttSet");

  for i = 1, 2 do
    gameObject_UpgradeLvs[i] = uiController:FindGameObject(string.Concat("UpgradeLv (", i, ")"));
  end

  gameObject_UpgradeItem = uiController:FindGameObject("UpgradeItem");
  gameObject_UpgradeSkill = uiController:FindGameObject("UpgradeSkill");

  --Role
  this.InitRole(uiController);

  --Status
  this.InitStatus(uiController);

  --Moral
  for i = 1, 5 do
    text_Morals[i] = uiController:FindText(string.Concat("Text_Moral (", i, ")"));
  end

  radar_Moral = UIStatusGameObject[EUIStatusGameObject.Moral].transform:Find("Radar"):GetComponent("UIPolygon");

  --Equip
  for i = 1, Role.maxEquip do
    item_Equips[i] = UIItemController.New(uiController:FindGameObject(string.Concat("Image_EquipBG (", i, ")")).transform, this.OnClick_Equip, i);
  end

  text_BarValue = uiController:FindText("Text_BarValue");

  tempEvent = uiController:FindEvent("Image_Switch");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchOutfitStyle);
  event_AllUnEquip = uiController:FindEvent("Image_AllUnEquip");
  event_AllUnEquip:SetListener(EventTriggerType.PointerClick, this.OnClick_AllUnEquip);

  switchOutfitStyle = uiController:FindGameObject("Image_Switch");
  image_SwitchOutfitStyle = uiController:FindImage("Image_Switch");

  --Astrolabe
  this.InitAstrolabe(uiController, tempEvent);

  --OutfitStyle
  this.InitOutfitStyle(uiController,tempEvent);

  --AttSet
  this.InitAttSet(uiController, tempEvent);

  --Details
  this.InitAllDetail(uiController, tempEvent);

  --Breakthrough
  this.InitBreakthrough(uiController, tempEvent);
end

function UIStatus.InitRole(uiController, tempEvent)
  uiCareer = UICareerController.New(uiController:FindGameObject("GameObject_Career"));

  jmpDrawer_Role = uiController:FindRawImage("RawImage_Role").gameObject:AddComponent(typeof(JmpDrawer));

  text_Name = uiController:FindText("Text_T6_Name");
  text_Lv = uiController:FindText("Text_T14_LvValue");
  image_UpgradeLv = uiController:FindImage("Image_UpgradeLv");
  text_UpgradeLv = uiController:FindText("Text_UpgradeLv");
  text_Hp = uiController:FindText("Text_T5_HpValue");
  text_Sp = uiController:FindText("Text_T5_SpValue");
  text_Exp = uiController:FindText("Text_T5_ExpValue");
  text_FullHp = uiController:FindText("Text_T5_HpFullValue");
  text_FullSp = uiController:FindText("Text_T5_SpFullValue");
  text_FullExp = uiController:FindText("Text_T5_ExpFullValue");

  slider_Hp = uiController:FindSlider("Slider_Hp");
  slider_Sp = uiController:FindSlider("Slider_Sp");
  slider_Exp = uiController:FindSlider("Slider_Exp");

  image_Prev =  uiController:FindGameObject("Image_Prev");
  tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = -1;

  image_Next =  uiController:FindGameObject("Image_Next");
  tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = 1;

  event_Share = uiController:FindEvent("Image_Share");
  event_Share:SetListener(EventTriggerType.PointerClick, this.OnClick_Share);

  event_ChgName = uiController:FindEvent("Image_ChgName");
  event_ChgName:SetListener(EventTriggerType.PointerClick, this.OnClick_ChgName);
end

function UIStatus.InitStatus(uiController)
  text_Point = uiController:FindText("Text_Point");
  text_AttributePoint = uiController:FindText("Text_AttributePoint");
  text_AttributeSubmit = uiController:FindText("Text_AttributeSubmit");
  text_AttributeReset = uiController:FindText("Text_AttributeReset");
  image_CurrentUpgradeLv = uiController:FindImage("Image_CurrentUpgradeLv");
  text_CurrentUpgradeLv = uiController:FindText("Text_CurrentUpgradeLv");
  image_NextUpgradeLv = uiController:FindImage("Image_NextUpgradeLv");
  text_NextUpgradeLv = uiController:FindText("Text_NextUpgradeLv");
  text_UpgradePoint = uiController:FindText("Text_UpgradePoint");
  text_UpgradeMoney = uiController:FindText("Text_UpgradeMoney");
  text_CurrentUpgradeSkill = uiController:FindText("Text_CurrentUpgradeSkill");
  text_NextUpgradeSkill = uiController:FindText("Text_NextUpgradeSkill");

  rawImage_CurrentUpgradeSkill = uiController:FindRawImage("RawImage_CurrentUpgradeSkill");
  rawImage_NextUpgradeSkill = uiController:FindRawImage("RawImage_NextUpgradeSkill");

  for i = 1, 2 do
    text_UpgradeItems[i] = uiController:FindText(string.Concat("Text_UpgradeItem (", i, ")"));
    item_Upgrades[i] = UIItemController.New(uiController:FindGameObject(string.Concat("Image_UpgradeItemBG (", i, ")")).transform, this.OnClick_UpgradeItem, i);
  end

  local attributeRoot = uiController:FindGameObject("Attribute");
  for i = 1, 6 do
    local tempParent = attributeRoot.transform:Find(string.Concat("Image_AttributeBG (", i, ")"));

    attComponents[i] = {};
    attComponents[i].add = 0;

    attComponents[i].text_Attribute = tempParent:Find("Text_Attribute"):GetComponent("Text");

    --lock
    attComponents[i].image_Lock = tempParent:Find("Image_Lock"):GetComponent("Image");

    attComponents[i].image_SetLock = tempParent:Find("Image_SetLock"):GetComponent("Image");

    attComponents[i].event_SetLock = tempParent:Find("Image_SetLock"):GetComponent("UIEvent");
    attComponents[i].event_SetLock:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeLock);
    attComponents[i].event_SetLock.parameter = i;

    --reborn
    attComponents[i].text_Reborn = tempParent:Find("Text_Reborn"):GetComponent("Text");

    --add
    attComponents[i].image_PointBG = tempParent:Find("Image_PointBG"):GetComponent("Image");
    attComponents[i].event_Enter = attComponents[i].image_PointBG:GetComponent("UIEvent");
    attComponents[i].event_Enter:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeEnter);
    attComponents[i].event_Enter.parameter = i;

    tempParent = attComponents[i].image_PointBG.transform;

    attComponents[i].text_Add = tempParent:Find("Text_Add"):GetComponent("Text");

    attComponents[i].event_Add = tempParent:Find("Image_Add"):GetComponent("UIEvent");
    attComponents[i].event_Add:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attComponents[i].event_Add.parameter = {["index"] = i, ["value"] = 1 };

    attComponents[i].event_Dec = tempParent:Find("Image_Dec"):GetComponent("UIEvent");
    attComponents[i].event_Dec:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attComponents[i].event_Dec.parameter = {["index"] = i, ["value"] = -1 };

    attComponents[i].event_FastAdd = tempParent:Find("Image_FastAdd"):GetComponent("UIEvent");
    attComponents[i].event_FastAdd:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attComponents[i].event_FastAdd.parameter = {["index"] = i, ["value"] = attributeFastPoint };

    attComponents[i].event_FastDec = tempParent:Find("Image_FastDec"):GetComponent("UIEvent");
    attComponents[i].event_FastDec:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attComponents[i].event_FastDec.parameter = {["index"] = i, ["value"] = -attributeFastPoint };

  end

  event_AttributeSubmit = uiController:FindEvent("Image_AttributeSubmit");
  event_AttributeSubmit:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeSubmit);

  event_AttributeReset = uiController:FindEvent("Image_AttributeReset");
  event_AttributeReset:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeReset);
end

function UIStatus.InitAstrolabe(uiController, tempEvent)
  local tempTransform = UIStatusGameObject[EUIStatusGameObject.Astrolabe].transform:Find("Attribute");
  text_AstrolabePoint = tempTransform:Find("Function"):Find("GameObject"):Find("Text_AstrolabePoint"):GetComponent("Text");
  text_AstrolabeMoney = tempTransform:Find("Function"):Find("GameObject"):Find("Text_AstrolabeMoney"):GetComponent("Text");

  for i = 1, Astrolabe.maxAstrolabe do
    text_AstrolabeAttribute[i] = tempTransform:Find(string.Concat("Image_AttributeBG (", i , ")")):Find("Text_Attribute"):GetComponent("Text");
    text_AstrolabeNextAttribute[i] = tempTransform:Find(string.Concat("Image_AttributeBG (", i , ")")):Find("Text_Reborn"):GetComponent("Text");
  end

  image_AstrolabeMask = uiController:FindImage("Image_AstrolabeMask");
  rawImage_AstrolabeBG = uiController:FindRawImage("RawImage_AstrolabeBG");
  rawImage_AstrolabePoint = uiController:FindRawImage("RawImage_AstrolabePoint");

  tempTransform = rawImage_AstrolabeBG.transform;
  for i = 1, Astrolabe.maxAstrolabe do
    rawImage_AstrolabeStar[i] = tempTransform:Find(string.Concat("RawImage_AstrolabeStar (", i , ")")):GetComponent("RawImage");
    rawImage_AstrolabeBall[i] = tempTransform:Find(string.Concat("RawImage_AstrolabeBall (", i , ")")):GetComponent("UIRawImage");
  end

  tempEvent = uiController:FindEvent("Image_AstrolabeUp");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AstrolabeUp);
end

function UIStatus.InitOutfitStyle(uiController, tempEvent)
  gameObject_styleTagInfo = uiController:FindGameObject("Text_StyleTagInfo");
  text_styleTagInfo = uiController:FindText("Text_StyleTagInfo");
  gameObject_switchOutfitStyleButText = uiController:FindGameObject("Text_SwitchOutfitStyleButText");
  text_switchOutfitStyleButText = uiController:FindText("Text_SwitchOutfitStyleButText");
  
  image_CategoryHead = uiController:FindImage("Image_Head");
  image_CategoryBody = uiController:FindImage("Image_Body");
  image_CategoryWrist = uiController:FindImage("Image_Wrist");
  image_CategoryFoot = uiController:FindImage("Image_Foot");
  image_CategoryHand = uiController:FindImage("Image_Hand");
  
  tempEvent = uiController:FindEvent("Image_Head");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
  tempEvent.parameter = EItemFitType.Style_Head;
  tempEvent = uiController:FindEvent("Image_Body");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
  tempEvent.parameter = EItemFitType.Style_Body;
  tempEvent = uiController:FindEvent("Image_Wrist");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
  tempEvent.parameter = EItemFitType.Style_Wrist;
  tempEvent = uiController:FindEvent("Image_Foot");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
  tempEvent.parameter = EItemFitType.Style_Boots;
  tempEvent = uiController:FindEvent("Image_Hand");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
  tempEvent.parameter = EItemFitType.Style_Hand;
  
  text_collectTitle = uiController:FindText("Text_CollectTitle");
end

function UIStatus.InitAttSet(uiController, tempEvent)
  local root = uiController:FindGameObject("AttSet");
  text_AttSetSumPoint = uiController:FindText("Text_AttSetSumPoint");
  text_AttSetRemainPoint = uiController:FindText("Text_AttSetRemainPoint");
  text_AttSetName = uiController:FindText("Text_AttSetName");

  gameObject_AttSetFunction = uiController:FindGameObject("AttSetFunction");
  gameObject_EditAttSetFunction = uiController:FindGameObject("EditAttSetFunction");

  tempEvent = uiController:FindEvent("Image_EditSetName");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeSetName);

  tempEvent = uiController:FindEvent("Image_EditAttSet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_EditAttSet);

  tempEvent = uiController:FindEvent("Image_ApplyAttSet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ApplyAttSet);

  tempEvent = uiController:FindEvent("Image_ClearAttSet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ClearAttSet);

  tempEvent = uiController:FindEvent("Image_CancelAttSet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CancelAttSet);
  
  tempEvent = uiController:FindEvent("Image_SaveAttSet");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SaveAttSet);

  local attributeRoot = root.transform:Find("Attribute");

  for i = 1, 6 do
    local tempParent = attributeRoot.transform:Find(string.Concat("Image_AttributeBG (", i, ")"));

    attSetComponents[i] = {};
    attSetComponents[i].add = 0;

    attSetComponents[i].text_Attribute = tempParent:Find("Text_Attribute"):GetComponent("Text");

    --add
    attSetComponents[i].image_PointBG = tempParent:Find("Image_PointBG"):GetComponent("Image");
    attSetComponents[i].event_Enter = attSetComponents[i].image_PointBG:GetComponent("UIEvent");
    attSetComponents[i].event_Enter:SetListener(EventTriggerType.PointerClick, this.OnClick_EnterAttSet);
    attSetComponents[i].event_Enter.parameter = i;

    tempParent = attSetComponents[i].image_PointBG.transform;

    attSetComponents[i].text_Add = tempParent:Find("Text_Add"):GetComponent("Text");

    attSetComponents[i].event_Add = tempParent:Find("Image_Add"):GetComponent("UIEvent");
    attSetComponents[i].event_Add:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attSetComponents[i].event_Add.parameter = {["index"] = i, ["value"] = 1 };

    attSetComponents[i].event_Dec = tempParent:Find("Image_Dec"):GetComponent("UIEvent");
    attSetComponents[i].event_Dec:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attSetComponents[i].event_Dec.parameter = {["index"] = i, ["value"] = -1 };

    attSetComponents[i].event_FastAdd = tempParent:Find("Image_FastAdd"):GetComponent("UIEvent");
    attSetComponents[i].event_FastAdd:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attSetComponents[i].event_FastAdd.parameter = {["index"] = i, ["value"] = attributeFastPoint };

    attSetComponents[i].event_FastDec = tempParent:Find("Image_FastDec"):GetComponent("UIEvent");
    attSetComponents[i].event_FastDec:SetListener(EventTriggerType.PointerClick, this.OnClick_AttributeChange);
    attSetComponents[i].event_FastDec.parameter = {["index"] = i, ["value"] = -attributeFastPoint };
  end
end

function UIStatus.InitAllDetail(uiController, tempEvent)
  --Detail
  detailUI = UIStatusDetail.New(uiController:FindGameObject("DetailRoot").transform);

  detailArrow = uiController:FindGameObject("DetailArrow");

  tempEvent = uiController:FindEvent("Image_Detail");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Detail);

  tempEvent = uiController:FindEvent("Image_DetailPrev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = -1;

  tempEvent = uiController:FindEvent("Image_DetailNext");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = 1;

  --OtherDetail
  gameObject_TreasureDetailRoot = uiController:FindGameObject("TreasureDetailRoot");
  treasureDetailUI = UITreasureDetail.New(gameObject_TreasureDetailRoot.transform);

  tempEvent = uiController:FindEvent("Image_OtherDetail");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_OtherDetail);

  image_OtherDetail = tempEvent:GetComponent("Image");
end

function UIStatus.InitBreakthrough(uiController, tempEvent)
  tempEvent = uiController:FindEvent("Image_ResetBreakthrough");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ResetBreakthrough);

  text_BreakthroughLevel = uiController:FindText("Text_BreakthroughLevel");

  scrollContent_Breakthrough = uiController:FindScrollContent("ScrollContent_Breakthrough");
  scrollContent_Breakthrough.onInitialize = this.OnInitialize_BreakthroughScrollContent;
  scrollContent_Breakthrough.onItemChange = this.OnItemChange_BreakthroughScrollContent;
  scrollContent_Breakthrough:Initialize(Breakthrough.GetBreakthroughLevel());

  scrollContent_AttSet = uiController:FindScrollContent("ScrollContent_AttSet");
  scrollContent_AttSet.onInitialize = this.OnInitialize_ScrollContentAttSet;
  scrollContent_AttSet.onItemChange = this.OnItemChange_ScrollContentAttSet;
  scrollContent_AttSet:Initialize(Status.GetAttSetCount());
end

function UIStatus.OnOpen(tag, role)
  if tag ~= nil then
    currentTag = tag;
  else
    currentTag = EUIStatusTag.Status;
  end

  if role == nil or role.kind == EHuman.Player then
    currentFollowIndex = 0;
    currentRole = Role.player;
  elseif role.kind == EHuman.FollowNpc then
    currentRole = role;
    for i = 1, Role.maxFollowNpc do
      if currentRole == Role.GetRole(EHuman.FollowNpc, Role.playerId, i) then
        currentFollowIndex = i;
        break;
      end
    end
  end

  showDetail = false;
  showTreasureDetail = false;
  upgradeNpcId = 0;

  return true;
end

function UIStatus.OnVisible(visible)
  if visible then
    table.Clear(tagDatas);
    tagDatas[EUIStatusTag.Status] = { string.Get(20262), nil };
    tagDatas[EUIStatusTag.Moral] = { string.Get(20323), nil };
    tagDatas[EUIStatusTag.Equip] = { string.Get(50331), nil };
    tagDatas[EUIStatusTag.Style] = { string.Get(99685), nil };
    tagDatas[EUIStatusTag.Astrolabe] = { string.Get(22602), nil };

    if HouseBoard.FeatureIsOpen() then
      EUIStatusTag.Profile = table.Count(tagDatas) + 1;
      tagDatas[EUIStatusTag.Profile] = { string.Get(23355), nil };
    else
      EUIStatusTag.Profile = 0;
    end

    if Breakthrough.CheckBreakthrough() then
      EUIStatusTag.Breakthrough = table.Count(tagDatas) + 1;
      tagDatas[EUIStatusTag.Breakthrough] = { string.Get(23571), nil };
    else
      EUIStatusTag.Breakthrough = 0;
    end

    EUIStatusTag.AttSet = table.Count(tagDatas) + 1;
    tagDatas[EUIStatusTag.AttSet] = {string.Get(24159), nil};

    tagOffset = 0;
    if currentTag == EUIStatusTag.Sell then
      tagOffset = EUIStatusTag.Sell - EUIStatusTag.Equip;
    elseif currentTag == EUIStatusTag.ChatPaste then
      tagOffset = EUIStatusTag.ChatPaste - EUIStatusTag.Equip;
    elseif currentTag == EUIStatusTag.FightEquip then
      tagOffset = EUIStatusTag.FightEquip - EUIStatusTag.Equip;
    elseif currentTag == EUIStatusTag.FightStyle then
      tagOffset = EUIStatusTag.FightStyle - EUIStatusTag.Style;
    end

    UIFrame.Active(transform_FrameRect, string.Get(20262), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagDatas, currentTag - tagOffset, 1, this.CheckRedDot);

    if currentFollowIndex == 0 then
      currentRole = Role.player;
    else
      currentRole = Role.GetRole(EHuman.FollowNpc, Role.playerId, currentFollowIndex);
    end

    astrolabeShowTimer = 0;
    LuaHelper.SetColor(image_AstrolabeMask, Color.Alpha);

    this.UpdateUI(true, true);
  end
end

function UIStatus.OnClose()
  jmpDrawer_Role:Disable();
  outfitStyleIdTempSave = 0;
  
  if Contains(currentTag, EUIStatusTag.Equip, EUIStatusTag.Style, EUIStatusTag.ChatPaste, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle, EUIStatusTag.Breakthrough) then
    UI.Close(UIBag);
  elseif currentTag == EUIStatusTag.Sell then
    UI.Close(UIBag);
    EventManager.SetConduct(true);
  elseif currentTag == EUIStatusTag.OutfitStyle then
    UI.Close(UIBag);
    currentOutfitSyleCategory = 0;
  end

  if HouseBoard.FeatureIsOpen() then
    if currentTag == EUIStatusTag.Profile then
      UI.Close(UIHouseBoard, true);
    end
  end

  if Hotkey.GetEditMode() ~= EHotkeyEditMode.None then
    Hotkey.Close();
  end

  if UI.IsVisible(UIAlert) then
    UI.Close(UIAlert);
  end
end

function UIStatus.OnClick_Close()
  UI.Close(UIStatus);

  return true;
end

function UIStatus.OnClick_Help(checkShow)
  if currentTag == EUIStatusTag.Status then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 11, this);
    end
  elseif currentTag == EUIStatusTag.Moral then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 12, this);
    end
  elseif currentTag == EUIStatusTag.Astrolabe then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 44, this);
    end
  elseif HouseBoard.FeatureIsOpen() and currentTag == EUIStatusTag.Profile then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 0, this, string.Get(23356), string.Get(23616));
    end
  elseif currentTag == EUIStatusTag.Style or currentTag == EUIStatusTag.OutfitStyle then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 59, this);
    end
  elseif currentTag == EUIStatusTag.AttSet then
    if checkShow ~= true then
      UI.Open(UITutorialHint, 77, this);
    end
  else
    return false;
  end

  return true;
end

function UIStatus.OnClick_Tag(mainTag, subTag)
  if Contains(currentTag, EUIStatusTag.Sell, EUIStatusTag.ChatPaste) then return false end

  if Contains(currentTag, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle) then
    if FightField.GetConFightRoleKind() == EHuman.FollowNpc then
      if not Contains(mainTag + tagOffset, EUIStatusTag.FightEquip) then
        return false;
      end
    else
      if not Contains(mainTag + tagOffset, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle) then
        return false;
      end
    end
  end

  currentTag = mainTag + tagOffset;

  if Contains(currentTag, EUIStatusTag.Style,EUIStatusTag.OutfitStyle, EUIStatusTag.FightStyle, EUIStatusTag.AttSet) then
    currentFollowIndex = 0;
    currentRole = Role.player;
  end

  upgradeNpcId = 0;

  this.UpdateUI(true, true);

  return true;
end

function UIStatus.CheckRedDot(mTag, subTag)
  if mainTag == mTag then
    return false;
  else
    mainTag = mTag;
  end

  if mainTag == EUIStatusTag.Status then
    return RedDot.Check(ERedDot.AttributePoint);
  elseif mainTag == EUIStatusTag.Equip then
    return RedDot.Check(ERedDot.Bag);
  elseif mainTag == EUIStatusTag.Breakthrough then
    return RedDot.Check(ERedDot.Breakthrough);
  end

  if HouseBoard.FeatureIsOpen() then
    if mainTag == EUIStatusTag.Profile then
      return RedDot.Check(ERedDot.FriendGift, ERedDot.CharmReward, ERedDot.GiftFeedback);
    end
  end

  return false;
end

  --region update
function UIStatus.UpdateUI(reopenBag, reset)
  if HouseBoard.FeatureIsOpen() then
    UIStatusGameObject[EUIStatusGameObject.Role]:SetActive(not Contains(currentTag, EUIStatusTag.Astrolabe, EUIStatusTag.Profile, EUIStatusTag.AttSet));
  else
    UIStatusGameObject[EUIStatusGameObject.Role]:SetActive(not Contains(currentTag, EUIStatusTag.Astrolabe, EUIStatusTag.AttSet));
  end

  UIStatusGameObject[EUIStatusGameObject.Bar]:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.Status, EUIStatusTag.Moral))
  UIStatusGameObject[EUIStatusGameObject.Status]:SetActive(currentTag == EUIStatusTag.Status);
  UIStatusGameObject[EUIStatusGameObject.Moral]:SetActive(currentTag == EUIStatusTag.Moral);
  UIStatusGameObject[EUIStatusGameObject.Equip]:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.Equip, EUIStatusTag.Style,EUIStatusTag.OutfitStyle, EUIStatusTag.Sell, EUIStatusTag.ChatPaste, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle));
  UIStatusGameObject[EUIStatusGameObject.Detail]:SetActive(showDetail);
  UIStatusGameObject[EUIStatusGameObject.BarValue]:SetActive(Contains(currentTag, EUIStatusTag.Equip, EUIStatusTag.Style, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle));
  UIStatusGameObject[EUIStatusGameObject.Astrolabe]:SetActive(currentTag == EUIStatusTag.Astrolabe);
  UIStatusGameObject[EUIStatusGameObject.Breakthrough]:SetActive(currentTag == EUIStatusTag.Breakthrough and Breakthrough.CheckBreakthrough());
  UIStatusGameObject[EUIStatusGameObject.OutfitStyle]:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.OutfitStyle));
  UIStatusGameObject[EUIStatusGameObject.AttSet]:SetActive(currentTag == EUIStatusTag.AttSet);

  event_AllUnEquip.gameObject:SetActive(false);
  switchOutfitStyle:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
  image_Prev:SetActive(not Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
  image_Next:SetActive(not Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
  gameObject_styleTagInfo:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
  gameObject_switchOutfitStyleButText:SetActive(not showDetail and Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
 
  if reopenBag == true then
    UI.Close(UIBag);
  end

  this.UpdateRole();
  this.UpdateStatus(reset);
  this.UpdateMoral();
  this.UpdateEquip(reopenBag);
  this.UpdateStyle(reopenBag);
  this.UpdateOutfitStyle(reopenBag);
  this.UpdateOutfitStyleHintAndIcon();
  this.UpdateSell();
  this.UpdateChatPaste();
  this.UpdateFightEquip(reopenBag);
  this.UpdateFightStyle(reopenBag);
  this.UpdateDetail();
  this.UpdateAstrolabe();
  this.UpdateHomePage();
  this.UpdateBreakthrough(reopenBag);
  this.UpdateAttSet(reset);
end

function UIStatus.UpdateRole()
  if currentRole == nil then return end

  uiCareer:SetCareer(currentRole:GetAttribute(EAttribute.Turn), currentRole:GetAttribute(EAttribute.Element), currentRole:GetAttribute(EAttribute.Career), currentRole.kind == EHuman.FollowNpc, currentRole:GetAttribute(EAttribute.Turn3Element));

  local npcData = npcDatas[currentRole.npcId]
  if npcData ~= nil then
    local offsetX, offsetY = npcData:GetOffset();

    jmpDrawer_Role:GenerateAndDraw(npcData:GetAtlasParams(ERolePose.Prepare + 1));
    jmpDrawer_Role.offsetY = offsetY;
  else
    jmpDrawer_Role:GenerateAndDraw(currentRole.data:GetAtlasParams(currentRole.titleId, ERolePose.Prepare + 1));
    jmpDrawer_Role.offsetY = 0;
  end

  if currentRole.kind == EHuman.FollowNpc then
    if currentRole.data.canGrow == 0 then
      text_Name.text = string.GetColorText(currentRole.name, Color.Orange);
    elseif currentRole.data.canGrow == 2 then
      text_Name.text = string.GetColorText(currentRole.name, Color.MissionNPC);
    else
      text_Name.text = currentRole.name;
    end
  else
    text_Name.text = currentRole.name;
  end

  text_Lv.text = currentRole:GetAttribute(EAttribute.Lv);

  if currentRole.kind == EHuman.FollowNpc and currentRole.data.upgradeLv > 0 then
    text_UpgradeLv.transform.parent.gameObject:SetActive(true);
    text_UpgradeLv.text = currentRole.data.upgradeLv;
    image_UpgradeLv.sprite = UI.GetUpgradeLvIcon(this.CheckDataGetUpgradeLvIcon(currentRole.data.upgradeLv));
  else
    text_UpgradeLv.transform.parent.gameObject:SetActive(false);
  end

  event_Share.gameObject:SetActive(currentRole.kind == EHuman.FollowNpc and currentTag == EUIStatusTag.ChatPaste);
  event_ChgName.gameObject:SetActive(not showDetail and currentTag ~= EUIStatusTag.Breakthrough and currentFollowIndex == 0);

  this.UpdateBar();
end

function UIStatus.UpdateBar()
  slider_Hp.maxValue = currentRole:GetAttribute(EAttribute.MaxHp);
  slider_Hp.value = currentRole:GetAttribute(EAttribute.Hp);

  text_Hp.text = string.Concat(math.floor((slider_Hp.value / slider_Hp.maxValue) * 100), "%");
  text_FullHp.text = string.Concat(slider_Hp.value, "/", slider_Hp.maxValue);

  slider_Sp.maxValue = currentRole:GetAttribute(EAttribute.MaxSp);
  slider_Sp.value = currentRole:GetAttribute(EAttribute.Sp);
  text_Sp.text = string.Concat(math.floor((slider_Sp.value / slider_Sp.maxValue) * 100), "%");
  text_FullSp.text = string.Concat(slider_Sp.value, "/", slider_Sp.maxValue);

  local turn = currentRole:GetAttribute(EAttribute.Turn);
  local level = currentRole:GetAttribute(EAttribute.Lv);
  local exp = currentRole:GetAttribute(EAttribute.Exp);

  if currentRole ~= Role.player then
    if turn == 2 then
      turn = 1;
    else
      turn = 0;
    end
  end

  if turn >= 3 then
    level = level - Role.playerMaxLv;
    exp = currentRole:GetAttribute(EAttribute.Turn3Exp);
  end

  this.SetExpSlider(slider_Exp, text_Exp, text_FullExp, level, exp, turn);

  text_BarValue.text = string.Concat(text_FullHp.text, "\n", text_FullSp.text, "\n", text_Exp.text);
end

function UIStatus.UpdateStatus(reset)
  if currentTag ~= EUIStatusTag.Status then return end
  if currentRole == nil then return end

  RedDot.Remove(ERedDot.AttributePoint);

  local baseValue;
  local equipAdd;
  local otherAdd;

  for i = 1, 6 do
    baseValue, equipAdd, otherAdd = Status.GetStatusAttribute(currentRole, i);
    attComponents[i].text_Attribute.text = baseValue + equipAdd + otherAdd;
  end

  if reset then
    for i = 1, 6 do
      attComponents[i].add = 0;
    end
  end

  this.UpdateStatusAdd();
end

function UIStatus.UpdateStatusAdd()
  event_AttributeReset:SetColor(Color.White);

  for i = 1, 2 do
    gameObject_UpgradeLvs[i]:SetActive(false);
  end
  gameObject_UpgradeItem:SetActive(false);
  gameObject_UpgradeSkill:SetActive(false);

  local turn = currentRole:GetAttribute(EAttribute.Turn);
  local level = currentRole:GetAttribute(EAttribute.Lv);
  local exp = currentRole:GetAttribute(EAttribute.Exp);

  if currentRole ~= Role.player then
    if turn == 2 then
      turn = 1;
    else
      turn = 0;
    end
  end

  if turn >= 3 then
    level = level - Role.playerMaxLv;
    exp = currentRole:GetAttribute(EAttribute.Turn3Exp);
  end

  if currentRole.kind == EHuman.Player then
    local point = currentRole:GetAttribute(EAttribute.AttrPoint);
    local count = 0;
    for i = 1, 6 do
      --lock
      attComponents[i].image_Lock.gameObject:SetActive(false);
      attComponents[i].image_SetLock.gameObject:SetActive(false);

      --reborn
      attComponents[i].text_Reborn.gameObject:SetActive(false);

      --add
      attComponents[i].image_PointBG.gameObject:SetActive(true);
      attComponents[i].text_Add.text = attComponents[i].add;

      count = count + attComponents[i].add;
    end

    --point
    text_Point.text = string.Get(22450);
    text_AttributePoint.text = point - count;

    --submit
    if count > 0 then
      event_AttributeSubmit.gameObject:SetActive(true);
      event_AttributeSubmit:SetColor(Color.White);

      text_AttributeSubmit.text = string.Get(20990);
      text_AttributeReset.text = string.Get(20073);
    else
      event_AttributeSubmit.gameObject:SetActive(false);

      text_AttributeReset.text = string.Get(20254);

      count = 0;
      count = count + currentRole:GetAttribute(EAttribute.Int);
      count = count + currentRole:GetAttribute(EAttribute.Atk);
      count = count + currentRole:GetAttribute(EAttribute.Def);
      count = count + currentRole:GetAttribute(EAttribute.Hpx);
      count = count + currentRole:GetAttribute(EAttribute.Spx);
      count = count + currentRole:GetAttribute(EAttribute.Agi);

      if count == 0 then
        event_AttributeReset:SetColor(Color.LightGray);
      end
    end
  else
    for i = 1, 6 do
      --lock
      attComponents[i].image_Lock.gameObject:SetActive(i == currentRole.data.graspPill.index);

      if currentRole.data.graspPill.index == 0 and level < Role.playerMaxLv and currentRole.data.graspPill.count < Role.npcMaxGraspPill then
        attComponents[i].image_SetLock.gameObject:SetActive(true);

        if Item.GetItemCount(46184 + i) > 0 then
          attComponents[i].image_SetLock.sprite = UI.GetSprite("but062L");
        else
          attComponents[i].image_SetLock.sprite = UI.GetSprite("but063D");
        end
      else
        attComponents[i].image_SetLock.gameObject:SetActive(false);
      end

      --reborn
      attComponents[i].text_Reborn.gameObject:SetActive(currentRole.npcId == Role.rebornNpcId);

      local oriValue = 0;
      local equipAdd = 0;
      local otherAdd = 0;
      local rebornValue = 0;

      if i == 1 then
        oriValue = currentRole:GetAttribute(EAttribute.Int);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipInt);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Int);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Int);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Int);
      elseif i == 2 then
        oriValue = currentRole:GetAttribute(EAttribute.Atk);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipAtk);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Atk);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Atk);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Atk);
      elseif i == 3 then
        oriValue = currentRole:GetAttribute(EAttribute.Def);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipDef);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Def);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Def);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Def);
      elseif i == 4 then
        oriValue = currentRole:GetAttribute(EAttribute.Hpx);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipHpx);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Hpx);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Hpx);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Hpx);
      elseif i == 5 then
        oriValue = currentRole:GetAttribute(EAttribute.Spx);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipSpx);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Spx);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Spx);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Spx);
      elseif i == 6 then
        oriValue = currentRole:GetAttribute(EAttribute.Agi);
        equipAdd = currentRole:GetAttribute(EAttribute.EquipAgi);
        if currentRole == Role.fightNpc then
          otherAdd = DrugBuff.GetAttribute(EDrugAttribute.Agi);
        end
        otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Agi);
        otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Agi);
      end

      if Role.rebornKind == 1 then
        rebornValue = oriValue + Role.rebornAttributes[i];
      elseif Role.rebornKind == 2 then
        rebornValue = npcDatas[currentRole.npcId].attributes[i] + Role.rebornAttributes[i];
      end

      if oriValue > rebornValue then
        attComponents[i].text_Reborn.text = string.GetColorText(tostring(rebornValue + equipAdd + otherAdd), Color.Red);
      elseif oriValue < rebornValue then
        attComponents[i].text_Reborn.text = string.GetColorText(tostring(rebornValue + equipAdd + otherAdd), Color.DarkGreen);
      else
        attComponents[i].text_Reborn.text = string.GetColorText(tostring(rebornValue + equipAdd + otherAdd), Color.Black);
      end

      --add
      attComponents[i].image_PointBG.gameObject:SetActive(false);
    end

    --faith
    text_Point.text = string.Get(20043);
    text_AttributePoint.text = currentRole:GetAttribute(EAttribute.Faith);

    --submit
    if currentRole.npcId == Role.rebornNpcId then
      event_AttributeSubmit.gameObject:SetActive(true);
      event_AttributeSubmit:SetColor(Color.White);

      if Role.rebornKind == 1 then
        for i = 1, 2 do
          gameObject_UpgradeLvs[i]:SetActive(true);
        end
        gameObject_UpgradeItem:SetActive(true);
        gameObject_UpgradeSkill:SetActive(true);


        text_UpgradeMoney.transform.parent.gameObject:SetActive(false);
        for i = 1, 2 do
          text_UpgradeItems[i].transform.parent.gameObject:SetActive(false);
        end

        rawImage_CurrentUpgradeSkill.transform.parent.gameObject:SetActive(true);
        if npcUpgradeDatas[currentRole.data.upgradeLv] ~= nil and skillDatas[npcDatas[currentRole.npcId].upgradeSkill] ~= nil then
          skillDatas[npcDatas[currentRole.npcId].upgradeSkill]:SetIcon(rawImage_CurrentUpgradeSkill);
          rawImage_CurrentUpgradeSkill.enabled = true;

          text_CurrentUpgradeSkill.text = npcUpgradeDatas[currentRole.data.upgradeLv].skillLv;
        else
          rawImage_CurrentUpgradeSkill.texture = nil;
          rawImage_CurrentUpgradeSkill.enabled = false;

          text_CurrentUpgradeSkill.text = "";
        end

        rawImage_NextUpgradeSkill.transform.parent.gameObject:SetActive(true);
        if npcUpgradeDatas[currentRole.data.upgradeLv + 1] ~= nil and skillDatas[npcDatas[currentRole.npcId].upgradeSkill] ~= nil then
          skillDatas[npcDatas[currentRole.npcId].upgradeSkill]:SetIcon(rawImage_NextUpgradeSkill);
          rawImage_NextUpgradeSkill.enabled = true;

          text_NextUpgradeSkill.text = npcUpgradeDatas[currentRole.data.upgradeLv + 1].skillLv;
        else
          rawImage_NextUpgradeSkill.texture = nil;
          rawImage_NextUpgradeSkill.enabled = false;

          text_NextUpgradeSkill.text = "";
        end

        text_CurrentUpgradeLv.text = currentRole.data.upgradeLv;
        text_NextUpgradeLv.text = currentRole.data.upgradeLv + 1;
        text_UpgradePoint.text = "";

        text_Point.text = "";
        text_AttributePoint.text = "";

        text_AttributeSubmit.text = string.Get(20990);
        text_AttributeReset.text = string.Get(20991);
      elseif Role.rebornKind == 2 then
        text_AttributeSubmit.text = string.Get(20990);
        text_AttributeReset.text = string.Get(20073);
      end

    else
      if npcDatas[currentRole.npcId].upgradeItemId ~= 0 then
        if currentRole.npcId == upgradeNpcId then
          for i = 1, 2 do
            gameObject_UpgradeLvs[i]:SetActive(true);
          end
          gameObject_UpgradeItem:SetActive(true);
          gameObject_UpgradeSkill:SetActive(true);

          rawImage_CurrentUpgradeSkill.transform.parent.gameObject:SetActive(false);
          rawImage_NextUpgradeSkill.transform.parent.gameObject:SetActive(false);

          if npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireMoney > 0 then
            text_UpgradeMoney.transform.parent.gameObject:SetActive(true);
            text_UpgradeMoney.text = npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireMoney;
          else
            text_UpgradeMoney.transform.parent.gameObject:SetActive(false);
          end

          if npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireDebris > 0 then
            text_UpgradeItems[1].transform.parent.gameObject:SetActive(true);
            text_UpgradeItems[1].text = string.Concat(RoleCount.Get(ERoleCount.NpcDebris), "/", npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireDebris);
            item_Upgrades[1]:SetItem(65411);
          else
            text_UpgradeItems[1].transform.parent.gameObject:SetActive(false);
            item_Upgrades[1]:SetItem(0);
          end

          if npcUpgradeDatas[currentRole.data.upgradeLv + 1].requirePicture > 0 then
            text_UpgradeItems[2].transform.parent.gameObject:SetActive(true);
            text_UpgradeItems[2].text = string.Concat(Item.GetItemCount(npcDatas[currentRole.npcId].upgradeItemId), "/", npcUpgradeDatas[currentRole.data.upgradeLv + 1].requirePicture);
            item_Upgrades[2]:SetItem(npcDatas[currentRole.npcId].upgradeItemId);
          else
            text_UpgradeItems[2].transform.parent.gameObject:SetActive(false);
            item_Upgrades[2]:SetItem(0);
          end

          text_CurrentUpgradeLv.text = currentRole.data.upgradeLv;
          image_CurrentUpgradeLv.sprite = UI.GetUpgradeLvIcon(this.CheckDataGetUpgradeLvIcon(currentRole.data.upgradeLv));
          text_NextUpgradeLv.text = currentRole.data.upgradeLv + 1;
          image_NextUpgradeLv.sprite = UI.GetUpgradeLvIcon(this.CheckDataGetUpgradeLvIcon(currentRole.data.upgradeLv + 1));
          text_UpgradePoint.text = string.format(string.Get(21135), npcUpgradeDatas[currentRole.data.upgradeLv + 1].attributeAdd);

          text_Point.text = "";
          text_AttributePoint.text = "";

          event_AttributeSubmit.gameObject:SetActive(true);
          event_AttributeSubmit:SetColor(Color.White);

          text_AttributeSubmit.text = string.Get(21133);
          text_AttributeReset.text = string.Get(20991);
        else
          event_AttributeSubmit.gameObject:SetActive(true);
          --檢查強化升階、強化等級&二轉
          if npcUpgradeDatas[currentRole.data.upgradeLv + 1] == nil or
                  (currentRole.data.upgradeLv >= 10 and currentRole:GetAttribute(EAttribute.Turn) < 2) then
            event_AttributeSubmit:SetColor(Color.LightGray);
          else
            event_AttributeSubmit:SetColor(Color.White);
          end

          if currentRole:GetAttribute(EAttribute.Lv) <= 1 then
            event_AttributeReset:SetColor(Color.LightGray);
          end

          text_AttributeSubmit.text = string.Get(10409);
          text_AttributeReset.text = string.Get(71293);
        end
      else
        event_AttributeSubmit.gameObject:SetActive(false);

        if currentRole:GetAttribute(EAttribute.Lv) <= 1 then
          event_AttributeReset:SetColor(Color.LightGray);
        end

        text_AttributeReset.text = string.Get(71293);
      end
    end
  end
end

function UIStatus.UpdateMoral()
  if currentTag ~= EUIStatusTag.Moral then return end
  if currentRole == nil then return end

  radar_Moral.gameObject:SetActive(false);
  for i = 1, 5 do
    local value = currentRole:GetAttribute(EAttribute.Moral, i);

    if currentRole ~= Role.player then
      value = math.max(value, 500);
    end

    text_Morals[i].text = value;
    radar_Moral.VerticesDistances[i - 1] = 0.2 + (value / 5000) * 1;
  end
  radar_Moral.gameObject:SetActive(true);
end

function UIStatus.UpdateEquip(reopenBag)
  if currentTag ~= EUIStatusTag.Equip then return end

  if reopenBag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        this.OpenItemInfo(itemId, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      nil,
      true,
      false
    );
  end

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(true);

  if currentRole == nil then return end

  for i = 1, Role.maxEquip do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, currentFollowIndex), i);
  end
end

function UIStatus.UpdateStyle(reopenBag)
  if currentTag ~= EUIStatusTag.Style then return end

  if reopenBag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        this.OpenItemInfo(itemId, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if itemDatas[itemSave.Id] == nil then return false end
        if itemSave.isVender then return false end
        if itemSave.isDeliver then return false end
        if fashionStressDatas[itemSave.Id] ~= nil then return true end

        return false;
      end,
      true,
      false
    );
  end

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(false);

  if currentRole == nil then return end

  for i = EItemFitType.Style_Head, EItemFitType.Style_Boots do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, 0), i - EItemFitType.Equip_Spec);
  end
end

function UIStatus.UpdateOutfitStyle(reopenbag)
  if currentTag ~= EUIStatusTag.OutfitStyle then return end
  --outfit背包

  if reopenbag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        this.OpenOutfitStyleInfo(itemId, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if itemDatas[itemSave.Id] == nil then return false end
        if not Item.IsStyle(itemDatas[itemSave.Id].fitType) then return false end
        if currentOutfitSyleCategory ~= 0 then
          if not Contains(itemDatas[itemSave.Id].fitType,currentOutfitSyleCategory) then return false end
        end
        if this.CheckOutfitStyleEquiped(itemSave.Id) then return false end
        return true;
      end,
      false,
      false,
      EThings.OutfitStyleInCollection
    );
  end

  --身上裝備
  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(false);

  if currentRole == nil then return end

  for i = EItemFitType.OutfitStyle_Head, EItemFitType.OutfitStyle_Boots do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, 0),i - EItemFitType.Equip_Spec - Role.maxOutfitStyle);
  end

end

function UIStatus.UpdateOutfitStyleHintAndIcon()
  if not Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.OutfitStyle) then return end

  if currentTag == EUIStatusTag.Style then
    image_SwitchOutfitStyle.sprite = UI.GetSprite("but261D");
    text_switchOutfitStyleButText.text = string.Get(23643);
    text_styleTagInfo.text = string.Get(23640);
  elseif  currentTag == EUIStatusTag.OutfitStyle then
    image_SwitchOutfitStyle.sprite = UI.GetSprite("but261L");
    image_CategoryHead.sprite = UI.GetSprite("but263D");
    image_CategoryBody.sprite = UI.GetSprite("but060D");
    image_CategoryWrist.sprite = UI.GetSprite("but262D");
    image_CategoryFoot.sprite = UI.GetSprite("but264D");
    image_CategoryHand.sprite = UI.GetSprite("but265D");
    if currentOutfitSyleCategory == EItemFitType.Style_Head then
      image_CategoryHead.sprite = UI.GetSprite("but263L");
    elseif currentOutfitSyleCategory == EItemFitType.Style_Body then
      image_CategoryBody.sprite = UI.GetSprite("but060L");
    elseif currentOutfitSyleCategory == EItemFitType.Style_Wrist then
      image_CategoryWrist.sprite = UI.GetSprite("but262L");
    elseif currentOutfitSyleCategory == EItemFitType.Style_Boots then
      image_CategoryFoot.sprite = UI.GetSprite("but264L");
    elseif currentOutfitSyleCategory == EItemFitType.Style_Hand then
      image_CategoryHand.sprite = UI.GetSprite("but265L");
    end
    text_switchOutfitStyleButText.text = string.Get(23642);
    text_styleTagInfo.text = string.Get(23641);
  end
end

function UIStatus.OpenItemInfo(itemId, bagIndex)
  local itemData = itemDatas[itemId];

  if Item.CheckItemUseState(itemId) then
    if Item.CheckHotKeyItemID(itemId) then
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(98026), this.OpenLauncherBar);
    elseif itemData.kind == EItemKind.CityWeapon then
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(22120), Item.DropItem);
    else
      convertPage = UILoginAward.GetConverItemPage(itemData.Id);
      if convertPage > 0 then
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), this.ShowConvertPage);
      else
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem);
      end
    end
  elseif BlissBagData.IsBlissBag(itemId) then
    if itemData.specialAbility  ==  EItemUseKind.LockBlissBag then
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(22005), this.ShowLockBlissBag);
    elseif itemData.specialAbility == EItemUseKind.ArmyBlissBag then
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(22885), Item.UseItem);
    else
      convertPage = UILoginAward.GetConverItemPage(itemData.Id);
      if convertPage > 0 then
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), this.ShowConvertPage, string.Get(40314), Item.UseItem);
      else
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(40314), Item.UseItem);
      end
    end
  elseif Item.CheckHotKeyItemID(itemId) then
    UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(40314), Item.UseItem, string.Get(98026), this.OpenLauncherBar);
  elseif itemData.specialAbility == EItemUseKind.Exchange and exchangeDatas[itemId] ~= nil then
    UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), Item.UseItem);
  elseif itemData.specialAbility == EItemUseKind.WeaponRecipe and exchangeDatas[itemId] ~= nil then
    UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), Item.UseItem);
  elseif itemData.kind == EItemKind.ExclusiveWeapon then
    if ExclusiveWeapon.CheckIsSkill(itemId) then
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle,string.Get(20165), Item.UseItem, string.Get(20869), ExclusiveWeapon.ViewSkill);
    else
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20165), Item.UseItem);
    end
  elseif Item.IsTypeOfEquips(itemData.fitType) then
    local styleFinded = false;
    local styleText = nil;
    local styleFunc = nil;
    local outfitStylefunction = nil;
    if Item.IsStyle(itemData.fitType) and fashionStressDatas[itemId] == nil then
      for k, v in pairs(collectStyleDatas) do
        for i = 1, 5 do
          if v.itemId[i] == itemId then
              styleFinded = true;
            if not CollectStyle.CheckEquiped(k, i) then
              styleText = string.Concat(string.Get(21408), string.Get(10607));
              styleFunc = function(bagKind, bagIndex, followIndex)
                CollectStyle.SendStoreStyle(k, i);
              end
            end
            outfitStylefunction = function(bagKind, bagIndex, followIndex)
              this.SendEquipOutfitStyleFromBag(k, i, itemId)
            end
            break;
          end
        end

        if styleFinded then
          break;
        end
      end
      if Item.IsDismantle(itemData) then
        convertPage = UILoginAward.GetConverItemPage(itemData.Id);
        if convertPage > 0 then
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(20253), this.ShowConvertPage, string.Get(20165), outfitStylefunction, styleText, styleFunc);
        else
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(20165), outfitStylefunction, styleText, styleFunc);
        end

      else
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20165), outfitStylefunction, styleText, styleFunc);
      end
    else
      if Item.IsDismantle(itemData) then
        convertPage = UILoginAward.GetConverItemPage(itemData.Id);
        if convertPage > 0 then
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(20253), this.ShowConvertPage, string.Get(20165), Item.UseItem);
        else
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(20165), Item.UseItem);
        end
      else
        convertPage = UILoginAward.GetConverItemPage(itemData.Id);
        if convertPage > 0 then
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), this.ShowConvertPage, string.Get(20165), Item.UseItem);
        else
          UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20165), Item.UseItem);
        end
      end
    end
  else
    if Item.IsDismantle(itemData) then
      convertPage = UILoginAward.GetConverItemPage(itemData.Id);
      if convertPage > 0 then
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(20253), this.ShowConvertPage, string.Get(40314), Item.UseItem);
      else
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(21026), Item.Dismantle, string.Get(40314), Item.UseItem);
      end

    else
      convertPage = UILoginAward.GetConverItemPage(itemData.Id);
      if convertPage > 0 then
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(20253), this.ShowConvertPage, string.Get(40314), Item.UseItem);
      else
        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, string.Get(98025), Item.DropItem, string.Get(40314), Item.UseItem);
      end
    end
  end
end

function UIStatus.ShowLockBlissBag(bagKind, bagIndex, followIndex)
  UIItemInfo.ShowLockBlissBag(bagIndex, followIndex);
end

function UIStatus.ShowRoleEquips(itemSave, i)
  if itemSave ~= nil and itemDatas[itemSave.Id] ~= nil then
    item_Equips[i]:SetItem(itemSave.Id, itemSave.quant, itemSave);
  else
    item_Equips[i]:SetItem(0);
  end
end

function UIStatus.UpdateSell()
  if currentTag ~= EUIStatusTag.Sell then return end

  UI.Open(UIBag,
    function(bagIndex, itemId)
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, 0,
        string.Get(30114),
        function(bagKind, bagIndex, followIndex)
          if not Contains(Role.player.war, EWar.None, EWar.Guest) then
            ShowCenterMessage(string.Get(80099));
            return;
          end

          UI.Close(UIItemInfo);

          local itemSave = Item.GetBagItem(EThings.Bag, bagIndex);
          if itemSave.quant > 1 then
            ShowStoreInput(itemSave.Id, itemSave.quant, ECurrency.Money, itemDatas[itemSave.Id].sellPrice,
              function(value)
                if value == nil or tonumber(value) <= 0 then return end

                --C:027-002 <賣東西> +背包索引(1) +數量(4)
                sendBuffer:Clear();
                sendBuffer:WriteByte(bagIndex);
                sendBuffer:WriteInt32(math.min(itemSave.quant, tonumber(value)));
                Network.Send(27, 2, sendBuffer);
              end
            );
          else
            --C:027-002 <賣東西> +背包索引(1) +數量(4)
            sendBuffer:Clear();
            sendBuffer:WriteByte(bagIndex);
            sendBuffer:WriteInt32(1);
            Network.Send(27, 2, sendBuffer);
          end
        end
      );
    end,
    Vector2.New(260, -25),
    false,
    nil,
    function(itemSave)
      if itemDatas[itemSave.Id] == nil then return false end
      if itemSave.isVender then return false end
      if itemSave.isDeliver then return false end
      if itemSave.isLock then return false end
      if bit.band(itemDatas[itemSave.Id].restrict, 16) >= 16 then return false end
      if itemDatas[itemSave.Id].sellPrice <= 0 then return false end

      return true;
    end,
    true,
    false
  );

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(true);

  if currentRole == nil then return end

  for i = 1, Role.maxEquip do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, currentFollowIndex), i);
  end
end

function UIStatus.UpdateChatPaste()
  if currentTag ~= EUIStatusTag.ChatPaste then return end

  UI.Open(UIBag,
    function(bagIndex, itemId)
      UI.Open(UIItemInfo, EThings.Bag, bagIndex, 0, string.Get(10093), Chat.AddItemMessage);
    end,
    Vector2.New(260, -25),
    false,
    nil,
    nil,
    true,
    false
  );

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(true);

  if currentRole == nil then return end

  for i = 1, Role.maxEquip do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, currentFollowIndex), i);
  end
end

function UIStatus.UpdateFightEquip(reopenBag)
  if currentTag ~= EUIStatusTag.FightEquip then return end

  if reopenBag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        local funName;
        if Item.IsTypeOfEquips(itemDatas[itemId].fitType) then
          funName = string.Get(20165); --裝備
        else
          funName = string.Get(40314); --使用
        end

        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, funName, Item.UseItem);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if itemSave.isVender then return false end
        if itemSave.isDeliver then return false end

        local itemData = itemDatas[itemSave.Id];
        if itemData == nil then return false end
        if itemData.openUsed == 1 then return false end
        if itemData.btnState > 0 and itemData.btnState ~= 11 then return false end

        if Contains(itemData.kind, EItemKind.Item_16, EItemKind.Item_17, EItemKind.Item_18, EItemKind.Item_50) then return true end
        if Item.IsEquip(itemData.fitType) then return true end
        if Contains(itemData.specialAbility, EItemUseKind.ItemUse_25, EItemUseKind.ItemUse_54, EItemUseKind.ItemUse_165) then return true end
        if bit.band(itemData.restrict2, 2) == 2 then return true end

        return false;
      end,
      true,
      false
    );
  end

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(true);

  if currentRole == nil then return end

  for i = 1, Role.maxEquip do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, currentFollowIndex), i);
  end
end

function UIStatus.UpdateFightStyle(reopenBag)
  if currentTag ~= EUIStatusTag.FightStyle then return end

  if reopenBag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        local funName;
        if Item.IsTypeOfEquips(itemDatas[itemId].fitType) then
          funName = string.Get(20165); --裝備
        else
          funName = string.Get(40314); --使用
        end

        UI.Open(UIItemInfo, EThings.Bag, bagIndex, currentFollowIndex, funName, Item.UseItem);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if itemSave.isVender then return false end
        if itemSave.isDeliver then return false end

        local itemData = itemDatas[itemSave.Id];
        if itemData == nil then return false end
        if itemData.openUsed == 1 then return false end
        if itemData.btnState > 0 and itemData.btnState ~= 11 then return false end

        if Item.IsStyle(itemData.fitType) and currentRole.kind == EHuman.Player then return true end

        return false;
      end,
      true,
      false
    );
  end

  item_Equips[EItemFitType.Equip_Spec].transform.parent.gameObject:SetActive(false);

  if currentRole == nil then return end

  for i = EItemFitType.Style_Head, EItemFitType.Style_Boots do
    this.ShowRoleEquips(Item.GetBagItem(EThings.Equip, i, 0), i - EItemFitType.Equip_Spec);
  end
end

function UIStatus.UpdateDetail()
  if not showDetail then
    showTreasureDetail = false;
    gameObject_TreasureDetailRoot:SetActive(showTreasureDetail);
    return;
  end

  if not showTreasureDetail then
    image_OtherDetail.sprite = UI.GetSprite("but318D");
  else
    image_OtherDetail.sprite = UI.GetSprite("but318L");
  end

  detailArrow:SetActive(not Contains(currentTag,EUIStatusTag.Breakthrough ,EUIStatusTag.Style, EUIStatusTag.OutfitStyle));
  gameObject_TreasureDetailRoot:SetActive(showTreasureDetail and Contains(currentTag, EUIStatusTag.Status ,EUIStatusTag.Moral));

  detailUI:ShowInfo(currentRole, attComponents);
  treasureDetailUI:ShowInfo();
end

function UIStatus.UpdateAstrolabe()
  if currentTag ~= EUIStatusTag.Astrolabe then return end

  local nextStar = Astrolabe.FindNextStar();

  local needPoint = 0;
  local needMoney = 0;

  if astrolabeShowTimer > 0 and not CGTimer.ContainsListener(this.UpdateAstrolabeAnimation) then
    CGTimer.AddListener(this.UpdateAstrolabeAnimation, CGTimer.deltaTime);
  else
    CGTimer.RemoveListener(this.UpdateAstrolabeAnimation);
  end

  if Astrolabe.starLv[nextStar] < Astrolabe.maxAstrolabeLv then
    needPoint = astrolabeDatas[nextStar].attributes[Astrolabe.starLv[nextStar] + 1].needPoint;
    needMoney = math.pow(Astrolabe.starLv[nextStar] + 1, 2) * 10000;
  end

  if Astrolabe.natalPoint >= needPoint then
    text_AstrolabePoint.text = string.Concat(Astrolabe.natalPoint, "/", needPoint);
  else
    text_AstrolabePoint.text = string.GetColorText(string.Concat(Astrolabe.natalPoint, "/", needPoint), Color.Red);
  end

  if Role.GetPlayerGold() >= needMoney then
    text_AstrolabeMoney.text = needMoney;
  else
    text_AstrolabeMoney.text = string.GetColorText(needMoney, Color.Red);
  end

  for i = 1, Astrolabe.maxAstrolabe do
    text_AstrolabeAttribute[i].text = string.Concat("+", Astrolabe.GetAttribute(i));

    if i == nextStar and Astrolabe.starLv[nextStar] < Astrolabe.maxAstrolabeLv then
      text_AstrolabeNextAttribute[i].gameObject:SetActive(true);
      text_AstrolabeNextAttribute[i].text = string.Concat("+", Astrolabe.GetAttribute(i, Astrolabe.starLv[nextStar] + 1));
    else
      text_AstrolabeNextAttribute[i].gameObject:SetActive(false);
    end
  end

  TextureManager.SetPng(ETextureUseType.UI, "Astrolabe_BG", rawImage_AstrolabeBG);
  TextureManager.SetPng(ETextureUseType.UI, "Astrolabe_BadoB07", rawImage_AstrolabePoint);

  for i = 1, Astrolabe.maxAstrolabe do
    TextureManager.SetPng(ETextureUseType.UI, "Astrolabe_Ball", rawImage_AstrolabeBall[i]);

    if nextStar > i or astrolabeShowTimer > 0 or Astrolabe.starLv[i] == Astrolabe.maxAstrolabeLv then
      TextureManager.SetPng(ETextureUseType.UI, string.format("Astrolabe_BadoB%02d", i), rawImage_AstrolabeStar[i]);
      rawImage_AstrolabeBall[i].gameObject:SetActive(true);
    else
      TextureManager.SetPng(ETextureUseType.UI, string.format("Astrolabe_BadoA%02d", i), rawImage_AstrolabeStar[i]);
      rawImage_AstrolabeBall[i].gameObject:SetActive(false);
    end
  end
end

function UIStatus.UpdateAstrolabeAnimation()
  if astrolabeShowTimer > 0 then
    astrolabeShowTimer = astrolabeShowTimer - CGTimer.deltaTime;

    if astrolabeShowTimer > 0 then
      LuaHelper.SetColor(image_AstrolabeMask, LuaHelper.GetColor(255, 255, 255, 255 * ((Astrolabe.levelUpShowTime - astrolabeShowTimer) / Astrolabe.levelUpShowTime)));
    else
      astrolabeShowTimer = 0;
      LuaHelper.SetColor(image_AstrolabeMask, Color.Alpha);

      ShowCenterMessage(string.Get(71331));
      this.UpdateAstrolabe();
    end
  end
end

function UIStatus.UpdateHomePage()
  if not HouseBoard.FeatureIsOpen() then return end
  if currentTag == EUIStatusTag.Profile then

    HouseBoard.SendOpenBoardRequest(Role.playerId);
  else
    UI.Close(UIHouseBoard, true);
  end
end

function UIStatus.UpdateBreakthrough(reopenBag)
  if currentTag ~= EUIStatusTag.Breakthrough then return end

  RedDot.Remove(ERedDot.Breakthrough);

  if currentRole ~= Role.player then
    currentRole = Role.player;
    text_Name.text = currentRole.name;
    text_Lv.text = currentRole:GetAttribute(EAttribute.Lv);
    uiCareer:SetCareer(currentRole:GetAttribute(EAttribute.Turn), currentRole:GetAttribute(EAttribute.Element), currentRole:GetAttribute(EAttribute.Career), currentRole.kind == EHuman.FollowNpc, currentRole:GetAttribute(EAttribute.Turn3Element));
  end

  if reopenBag == true then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        this.OpenItemInfo(itemId, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      nil,
      true,
      false
    );
  end

  Breakthrough.UpdateBreakthroughBar();
  UIStatus.RefreshBreakthrough();

  text_BreakthroughLevel.text = Breakthrough.GetBreakthroughLevelText();
end

function UIStatus.UpdateAttSet(reset)
  if currentTag ~= EUIStatusTag.AttSet then return end

  if reset then
    this.UpdateAttSetEditing(false);
  else
    this.UpdateAttSetEditing();
  end

  this.UpdateAttSetAttribute();
  this.UpdateAttSetAdd();
  this.UpdateAttSetName();
end

function UIStatus.UpdateAttSetName()
  text_AttSetName.text = Status.GetCurrentAttSet().name;
  scrollContent_AttSet:Refresh();
end

function UIStatus.UpdateAttSetAttribute()
  local baseValue;
  local equipAdd;
  local otherAdd;
  local attSet = Status.GetCurrentAttSet();
  local sumPoints = 0;

  for i = 1, 6 do
    baseValue, equipAdd, otherAdd = Status.GetStatusAttribute(currentRole, i);
    baseValue = attSet.att[i];
    sumPoints = sumPoints + baseValue;

    local attText;

    if equipAdd >= 0 then
      attText = string.Concat(" + ", equipAdd)
    else
      attText = equipAdd;
    end

    if otherAdd >= 0 then
      attText = string.Concat(attText, " + ", otherAdd)
    else
      attText = string.Concat(attText, otherAdd)
    end

    attText = string.Concat(attText, " = ", (baseValue + equipAdd + otherAdd));
    attText = string.GetColorText(attText, Color.Black);
    attText = string.Concat(baseValue, attText);
    attSetComponents[i].text_Attribute.text = attText;
  end

  text_AttSetName.text = attSet.name;
  text_AttSetSumPoint.text = sumPoints;
end

function UIStatus.UpdateAttSetEditing(editing)
  if editing ~= nil then
    editingAttSet = editing;
  end

  text_AttSetRemainPoint.gameObject:SetActive(editingAttSet);
  gameObject_AttSetFunction:SetActive(not editingAttSet);
  gameObject_EditAttSetFunction:SetActive(editingAttSet);

  for i = 1, 6 do
    attSetComponents[i].image_PointBG.gameObject:SetActive(editingAttSet);
  end
end

function UIStatus.UpdateAttSetAdd()
  if editingAttSet then
    attSetRemainPoint = currentRole:GetAttribute(EAttribute.AttrPoint);

    for i = 1, 6 do
      attSetComponents[i].text_Add.text = attSetComponents[i].add;
      attSetRemainPoint = attSetRemainPoint + Status.GetStatusAttribute(currentRole, i) - attSetComponents[i].add;
    end

    if attSetRemainPoint < 0 then
      text_AttSetRemainPoint.text = string.GetColorText(attSetRemainPoint, Color.Red);
    else
      text_AttSetRemainPoint.text = attSetRemainPoint;
    end
  end
end

--endregion

  --region onclick
function UIStatus.OnClick_Equip(uiEvent)
  if currentTag == EUIStatusTag.Sell then return end
  if not Contains(currentTag, EUIStatusTag.Equip, EUIStatusTag.Style, EUIStatusTag.OutfitStyle, EUIStatusTag.ChatPaste, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle) then return end
  if currentRole == nil then return end

  local fitType = uiEvent.parameter;

  if Contains(currentTag, EUIStatusTag.Style, EUIStatusTag.FightStyle) then
    fitType = fitType + EItemFitType.Equip_Spec;
  elseif currentTag == EUIStatusTag.OutfitStyle then
    fitType = fitType + EItemFitType.Equip_Spec + Role.maxOutfitStyle;
  end

  local  itemSave;
  if currentTag ~= EUIStatusTag.OutfitStyle then
    itemSave = Item.GetBagItem(EThings.Equip, fitType, currentFollowIndex);
  end

  if currentTag == EUIStatusTag.ChatPaste then
    UI.Open(UIItemInfo, EThings.Equip, fitType, currentFollowIndex, string.Get(10093), Chat.AddItemMessage);
  elseif Contains(currentTag, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle) then
    -- 判斷戰鬥是否為天空競技場，禁止天空競技場NPC卸下裝備
    if Role.player.war == EWar.ChaosGod then
      local fightRole = FightField.fightHum[FightField.conIdx];
      if fightRole ~= nil and fightRole.kind == EHuman.ChaosGod then
        ShowCenterMessage(string.Get(22493));
      end
    else
      if itemSave ~= nil and itemDatas[itemSave.Id] ~= nil and itemDatas[itemSave.Id].kind == EItemKind.ExclusiveWeapon then
        UI.Open(UIItemInfo, EThings.Equip, fitType, currentFollowIndex, string.Get(98003), Item.UnEquip, string.Get(20869), ExclusiveWeapon.ViewSkill);
      else
        UI.Open(UIItemInfo, EThings.Equip, fitType, currentFollowIndex, string.Get(98003), Item.UnEquip);
      end
    end
  elseif currentTag == EUIStatusTag.OutfitStyle then
    UI.Open(UIItemInfo, EThings.Equip, fitType, 0, string.Get(98003), this.SendUnEquipOutfitStyle);
  else
    if itemSave ~= nil and itemDatas[itemSave.Id] ~= nil and itemDatas[itemSave.Id].kind == EItemKind.ExclusiveWeapon then
      UI.Open(UIItemInfo, EThings.Equip, fitType, currentFollowIndex, string.Get(98003), Item.UnEquip, string.Get(20869), ExclusiveWeapon.ViewSkill);
    else
      UI.Open(UIItemInfo, EThings.Equip, fitType, currentFollowIndex, string.Get(98003), Item.UnEquip);
    end
  end
end

function UIStatus.OnClick_SwitchRole(uiEvent)
  if Contains(currentTag, EUIStatusTag.Style,EUIStatusTag.OutfitStyle, EUIStatusTag.Sell) then return end
  if Contains(currentTag, EUIStatusTag.FightEquip, EUIStatusTag.FightStyle) then
    ShowCenterMessage(string.Get(20590));
    return;
  end

  currentRole = nil;

  if uiEvent.parameter > 0 then
    for i = currentFollowIndex + 1, Role.maxFollowNpc do
      currentRole = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);

      if currentRole ~= nil then
        currentFollowIndex = i;
        break;
      end
    end
  else
    if currentFollowIndex == 0 then
      currentFollowIndex = Role.maxFollowNpc + 1;
    end

    for i = currentFollowIndex - 1, 1, -1 do
      currentRole = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);

      if currentRole ~= nil then
        currentFollowIndex = i;
        break;
      end
    end
  end

  if currentRole == nil then
    currentFollowIndex = 0;
    currentRole = Role.player;
  end

  upgradeNpcId = 0;

  this.UpdateUI(nil, true);
end

function UIStatus.OnClick_Share()
  Chat.AddNpcMessage(currentRole);
end

function UIStatus.OnClick_AttributeEnter(uiEvent)
  local index = uiEvent.parameter;

  if tonumber(text_AttributePoint.text) < 1 and attComponents[index].add < 1 then return end

  ShowCountInput(string.Get(21280), 0, tonumber(text_AttributePoint.text) + attComponents[index].add,
    function(text)
      if text == nil then return end
      attComponents[index].add = tonumber(text);
      this.UpdateStatusAdd();
      this.UpdateDetail();
    end
  );
end

function UIStatus.OnClick_AttributeLock(uiEvent)
  if currentRole.kind ~= EHuman.Player then
    --領悟丹
    if Contains(currentRole.data.canGrow, 0, 2) then
      ShowCenterMessage(string.Get(40333));
      return;
    end

    if currentRole.data.graspPill.index ~= 0 then
      ShowCenterMessage("GraspPill is locked !!");
      return;
    end

    if currentRole.data.graspPill.count >= Role.npcMaxGraspPill then
      ShowCenterMessage("GraspPill can't use any more !!");
      return;
    end

    if uiEvent.parameter == 1 then
      UISell.Launch(18, nil, currentRole.index);
    elseif uiEvent.parameter == 2 then
      UISell.Launch(19, nil, currentRole.index);
    elseif uiEvent.parameter == 3 then
      UISell.Launch(20, nil, currentRole.index);
    elseif uiEvent.parameter == 4 then
      UISell.Launch(21, nil, currentRole.index);
    elseif uiEvent.parameter == 5 then
      UISell.Launch(22, nil, currentRole.index);
    elseif uiEvent.parameter == 6 then
      UISell.Launch(23, nil, currentRole.index);
    end
  end
end

function UIStatus.OnClick_AttributeSubmit()
  if currentRole.kind == EHuman.Player then
    --確認投點
    if not Contains(Role.player.war, EWar.None, EWar.Guest) then
      ShowCenterMessage(string.Get(80099));
      return;
    end

    --檢查是否被關監
    if SceneManager.sceneId == 10701 then
      ShowCenterMessage(string.Get(40097), 1.2);
      return;
    end

    for i = 1, 6 do
      Status.SendAddAttribute(currentRole, i, attComponents[i].add);
    end
    this.UpdateStatus();
  else
    if currentRole.npcId == Role.rebornNpcId then
      if Role.rebornKind == 1 then
        --C:074-002 <武將確定強化>
        Network.Send(74, 2);
      elseif Role.rebornKind == 2 then
        --C:008-011 <脫胎換骨確認> +武將索引(1) +結果(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(currentRole.index);
        sendBuffer:WriteByte(1);
        Network.Send(8, 11, sendBuffer);
      end
    else
      if npcDatas[Role.rebornNpcId] ~= nil then
        if Role.rebornKind == 1 then
          ShowCenterMessage(string.format(string.Get(71288), npcDatas[Role.rebornNpcId].name, string.Get(71290), string.Get(71292)));
        elseif Role.rebornKind == 2 then
          ShowCenterMessage(string.format(string.Get(71288), npcDatas[Role.rebornNpcId].name, string.Get(71291), string.Get(71292)));
        end

        return;
      end

      if currentRole.npcId == upgradeNpcId then
        if npcDatas[currentRole.npcId].upgradeItemId == 0 then
          ShowCenterMessage("picture not found !!");
          return;
        end

        if npcUpgradeDatas[currentRole.data.upgradeLv + 1] == nil then
          return;
        end

        if Role.GetPlayerGold() < npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireMoney then
          ShowCenterMessage(string.Get(80401));
          return;
        end

        if RoleCount.Get(ERoleCount.NpcDebris) < npcUpgradeDatas[currentRole.data.upgradeLv + 1].requireDebris then
          ShowCenterMessage(string.Get(21086));
          return;
        end

        if Item.GetItemCount(npcDatas[currentRole.npcId].upgradeItemId) < npcUpgradeDatas[currentRole.data.upgradeLv + 1].requirePicture then
          ShowCenterMessage(string.Get(21086));
          return;
        end

        upgradeNpcId = 0;

        --C:074-001 <武將強化> +武將索引(1) +NPCID(2)
        sendBuffer:Clear();
        sendBuffer:WriteByte(currentRole.index);
        sendBuffer:WriteUInt16(currentRole.npcId);
        Network.Send(74, 1, sendBuffer);
      else
        if npcUpgradeDatas[currentRole.data.upgradeLv + 1] == nil then
          return;
        end
        --2轉後才能升11-15
        if currentRole.data.upgradeLv >= 10 and currentRole:GetAttribute(EAttribute.Turn) < 2 then
          return;
        end

        if Contains(currentRole.data.canGrow, 0, 2) then
          ShowCenterMessage(string.Get(40333));
          return;
        end

        upgradeNpcId = currentRole.npcId;
        this.UpdateStatus();
      end
    end
  end
end

function UIStatus.OnClick_AttributeReset()
  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if currentRole.kind == EHuman.Player then
    if tonumber(text_AttributePoint.text) < currentRole:GetAttribute(EAttribute.AttrPoint) then
      --取消投點
      for i = 1, 6 do
        attComponents[i].add = 0;
      end

      this.UpdateStatusAdd();
      this.UpdateDetail();
    else
      --重置點數
      local count = 0;
      count = count + currentRole:GetAttribute(EAttribute.Int);
      count = count + currentRole:GetAttribute(EAttribute.Atk);
      count = count + currentRole:GetAttribute(EAttribute.Def);
      count = count + currentRole:GetAttribute(EAttribute.Hpx);
      count = count + currentRole:GetAttribute(EAttribute.Spx);
      count = count + currentRole:GetAttribute(EAttribute.Agi);

      if count == 0 then return end
      Status.SendResetAttribute();
    end
  else
    if currentRole.npcId == Role.rebornNpcId then
      if Role.rebornKind == 1 then
        ShowCheckMessage(
          function(result)
            if result then
              --C:074-003 <武將取消強化>
              Network.Send(74, 3);
            end
          end,
          string.Get(21085),
          Role.player
        )
      elseif Role.rebornKind == 2 then
        --C:008-011 <脫胎換骨確認> +武將索引(1) +結果(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(currentRole.index);
        sendBuffer:WriteByte(2);
        Network.Send(8, 11, sendBuffer);
      end
    else
      if currentRole.npcId == upgradeNpcId then
        upgradeNpcId = 0;
        this.UpdateStatus();
      else
        if currentRole:GetAttribute(EAttribute.Lv) <= 1 then return end

        if npcDatas[Role.rebornNpcId] ~= nil then
          if Role.rebornKind == 1 then
            ShowCenterMessage(string.format(string.Get(71288), npcDatas[Role.rebornNpcId].name, string.Get(71290), string.Get(71293)));
          elseif Role.rebornKind == 2 then
            ShowCenterMessage(string.format(string.Get(71288), npcDatas[Role.rebornNpcId].name, string.Get(71291), string.Get(71293)));
          end

          return;
        end

        if Contains(currentRole.data.canGrow, 0, 2) then
          ShowCenterMessage(string.Get(40333));
          return;
        end

        --脫胎換骨
        UISell.Launch(16, nil, currentRole.index);
      end
    end
  end
end

function UIStatus.OnClick_AttributeChange(uiEvent)
  local index = uiEvent.parameter.index;
  local change = uiEvent.parameter.value;
  local points;
  if currentTag == EUIStatusTag.Status then
    points = tonumber(text_AttributePoint.text);
  end

  if currentTag == EUIStatusTag.Status then
    local add = Status.ChangeAtt(attComponents[index].add, change, points);
    attComponents[index].add = add;
    this.UpdateStatusAdd();
    this.UpdateDetail();
  elseif currentTag == EUIStatusTag.AttSet then
    local add = Status.ChangeAtt(attSetComponents[index].add, change);
    attSetComponents[index].add = add;
    this.UpdateAttSetAdd();
  end
end

function UIStatus.OnClick_Detail()
  if currentTag == EUIStatusTag.Sell then return end

  showDetail = not showDetail;

  this.UpdateUI();
end

function UIStatus.OnClick_OtherDetail()
  if not showDetail or not Contains(currentTag, EUIStatusTag.Status ,EUIStatusTag.Moral) then return end

  showTreasureDetail = not showTreasureDetail;

  this.UpdateDetail();
end

function UIStatus.OnClick_ChgName()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  ShowNameInput("",
    function(text)
      if string.IsNullOrEmpty(text) then return end
      if not string.CheckName(text) then return end
      if Role.player.war ~= EWar.None then
        ShowCenterMessage(string.Get(80099));
        return;
      end

      --C:067-001 <檢查名字> +L(1) +名字(L)
      sendBuffer:Clear();
      sendBuffer:WriteStringWithByteL(text);
      Network.Send(67, 1, sendBuffer);
    end
  );

  this.UpdateUI();
end

function UIStatus.OnClick_AstrolabeUp()
  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if astrolabeShowTimer > 0 then return end

  if Astrolabe.starLv[Astrolabe.maxAstrolabe] >= Astrolabe.maxAstrolabeLv then
    ShowCenterMessage(string.Get(71332));
    return;
  end

  local nextStar = Astrolabe.FindNextStar();
  local nextLv = Astrolabe.starLv[nextStar] + 1;
  local needPoint = 0
  if Astrolabe.starLv[nextStar] < Astrolabe.maxAstrolabeLv then
    needPoint = astrolabeDatas[nextStar].attributes[nextLv].needPoint;
  end

  if Astrolabe.natalPoint < needPoint then
    ShowCenterMessage(string.Get(71333));
    return;
  end

  if Role.GetPlayerGold() < nextLv * nextLv * 10000 then
    ShowCenterMessage(string.Get(71334));
    return;
  end

  --C:078-001 <命盤升級> +等級(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(nextStar);
  Network.Send(78, 1, sendBuffer);
end

function UIStatus.OnClick_ChangeCategory(uiEvent)
  if currentOutfitSyleCategory ~= uiEvent.parameter then
    currentOutfitSyleCategory = uiEvent.parameter;
  else
    currentOutfitSyleCategory = 0;
  end

  this.UpdateUI(true);
end

function UIStatus.OnClick_SwitchOutfitStyle()
  if currentTag == EUIStatusTag.Style then
    currentTag = EUIStatusTag.OutfitStyle;
    ShowCenterMessage(string.Get(23637));
  else
    currentOutfitSyleCategory = 0;
    currentTag = EUIStatusTag.Style;
    ShowCenterMessage(string.Get(23638));
  end

  this.UpdateUI(true);
end

function UIStatus.OnClick_AllUnEquip()
  Item.UnEquipAll();
end

function UIStatus.GetCurrentFollowIndex()
  return currentFollowIndex;
end

function UIStatus.OnClick_EnterAttSet(uiEvent)
  local index = uiEvent.parameter;
  ShowCountInput(string.Get(21280), 0, Status.GetAttSetMaxPoints(),
    function(text)
      if text == nil then return end
      attSetComponents[index].add = tonumber(text);
      this.UpdateAttSetAdd();
    end
  );
end

function UIStatus.OnClick_SelectAttSet(uiEvent)
  if editingAttSet then return end

  Status.SelectAttSet(uiEvent.parameter);
  local attSet = Status.GetCurrentAttSet();

  for i = 1, 6 do
    attSetComponents[i].add = attSet.att[i];
  end

  this.UpdateAttSet();
end

function UIStatus.OnClick_ChangeSetName(uiEvent)
  ShowNameInput(string.Get(24159),
    function(name)
      if name == nil then return end
      Status.ChangeAttSetName(name);
    end,
    12);
end

function UIStatus.OnClick_EditAttSet()
  this.UpdateAttSetEditing(true);
  this.ResetAttSetAdd();
  this.UpdateAttSetAdd();
end

function UIStatus.OnClick_ApplyAttSet()
  Status.ApplyAttSet(currentRole);
end

function UIStatus.OnClick_ClearAttSet()
  for i = 1, 6 do
    attSetComponents[i].add = 0;
  end

  this.UpdateAttSetAdd();
end

function UIStatus.OnClick_CancelAttSet()
  this.UpdateAttSetEditing(false);
  this.UpdateAttSetAttribute();
end

function UIStatus.OnClick_SaveAttSet()
  if attSetRemainPoint < 0 then
    ShowCenterMessage(string.Get(24508));
    return;
  end

  for i = 1, 6 do
    Status.SetAttributeSet(i, attSetComponents[i].add);
  end

  Status.SaveCurrentAttSet();
  this.UpdateAttSetEditing(false);
  this.UpdateAttSetAttribute();
end

function UIStatus.OnClick_ResetBreakthrough()
  UISell.Launch(62);
end

--endregion

function UIStatus.OnInitialize_BreakthroughScrollContent(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Breakthrough[itemIndex] = {};
    scrollItems_Breakthrough[itemIndex].item = UIItemController.New(scrollItems[itemIndex]:Find("Image_Item"));
    scrollItems_Breakthrough[itemIndex].slider = scrollItems[itemIndex]:Find("Slider_BreakthroughExp"):GetComponent("UISlider");
    scrollItems_Breakthrough[itemIndex].value = scrollItems[itemIndex]:Find("Text_BreakthroughExpValue"):GetComponent("Text");
    scrollItems_Breakthrough[itemIndex].fullValue = scrollItems[itemIndex]:Find("Text_BreakthroughExpFullValue"):GetComponent("Text");
  end
end

function UIStatus.OnItemChange_BreakthroughScrollContent(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local data = Breakthrough.GetBreakthrough(index);
  if data == nil then return false end
  scrollItems_Breakthrough[itemIndex].item:SetItem(data.itemID, data.itemCount);
  scrollItems_Breakthrough[itemIndex].item:SetCheckMark(data.complete);
  local slider = scrollItems_Breakthrough[itemIndex].slider;
  slider.maxValue = data.exp;
  slider.value = data.currentExp;
  scrollItems_Breakthrough[itemIndex].value.text = data.expValueText;
  scrollItems_Breakthrough[itemIndex].fullValue.text = data.expFullValueText;
  return true
end

function UIStatus.OnInitialize_ScrollContentAttSet(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_AttSet[itemIndex] = {};
    local scrollItem = scrollItems_AttSet[itemIndex];
    scrollItem.setName = scrollItems[itemIndex]:Find("Text_SetName"):GetComponent("Text");
    scrollItem.image_Select = scrollItems[itemIndex]:Find("Image_Select"):GetComponent("Image");
    scrollItem.event = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItem.event:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectAttSet);
  end
end

function UIStatus.OnItemChange_ScrollContentAttSet(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local scrollItem = scrollItems_AttSet[itemIndex];
  scrollItem.setName.text = Status.GetAttSetNames(index);
  scrollItem.event.parameter = index;
  scrollItem.image_Select.enabled = index == Status.GetCurrentAttSetIndex();
  return true
end

function UIStatus.ShowConvertPage()
  UILoginAward.ShowConvertPage(convertPage);
end

function UIStatus.OpenLauncherBar(bagKind, bagIndex, followIndex)
  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  local itemSave = Item.GetBagItem(bagKind, bagIndex);
  if itemSave == nil then return end

  Hotkey.SetCurrentSelectId(itemSave.Id, EHotkeyUI.Sub);
  Hotkey.Show(EHotkeyMode.ItemInfo);
end

function UIStatus.OpenOutfitStyleInfo(itemId, bagIndex)
  if currentTag ~= EUIStatusTag.OutfitStyle then return end

  local outfitFunction;

  for k, v in pairs(collectStyleDatas) do
    for i = 1, 5 do
      if v.itemId[i] == itemId then
        outfitFunction = function()
          this.SendEquipOutfitStyle(k,i);
        end
        break;
      end
    end
  end

  UI.Open(UIItemInfo, EThings.None, itemId, itemDatas[itemId], string.Get(20165),outfitFunction);
end

function UIStatus.OpenProfile()
  if currentTag == EUIStatusTag.Profile then
    UI.Open(UIHouseBoard);
  end
end

function UIStatus.ShowLockBlissBag(bagKind, bagIndex, followIndex)
  UIItemInfo.ShowLockBlissBag(bagIndex, followIndex);
end

function UIStatus.ShowRoleEquips(itemSave, i)
  if itemSave ~= nil and itemDatas[itemSave.Id] ~= nil then
    item_Equips[i]:SetItem(itemSave.Id, itemSave.quant, itemSave);
  else
    item_Equips[i]:SetItem(0);
  end
end

function UIStatus.SendEquipOutfitStyle(id, index) --收集冊ID, 部位
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  --C:023-124 <穿顯示時裝外觀>+ 收集冊ID(2) + 部位索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);
  sendBuffer:WriteByte(index);
  Network.Send(23, 124, sendBuffer);
end

function UIStatus.SendUnEquipOutfitStyle(bagKind, bagIndex, followIndex) --
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if BattleRoyale.InBattleRoyaleScene() then
    return
  end

  local index = bagIndex - EItemFitType.Equip_Spec  - Role.maxOutfitStyle
  
  --C:023-125 <脫顯示時裝外觀>+ 部位索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(index);
  Network.Send(23, 125, sendBuffer);
end

function UIStatus.SendEquipOutfitStyleFromBag(id, index, itemId) --時裝id, 部位
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  outfitStyleIdTempSave = itemId;
  
  --C:023-126 <從背包直接穿顯示時裝外觀>+ 收集冊ID(2) + 部位索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);
  sendBuffer:WriteByte(index);
  Network.Send(23, 126, sendBuffer);
end

function UIStatus.IsCurrentTag(tag)
  return tag == currentTag;
end

function UIStatus.IsCurrentRole(role)
  return role == currentRole;
end

function UIStatus.SetExpSlider(sliderExp, textExp, textFullExp, level, exp, turn)
  if (turn >= 3 and level >= Role.playerMaxLv + Role.playerTurn3MaxLv) or (turn < 3 and level == Role.playerMaxLv) then
    sliderExp.maxValue = 1;
    sliderExp.value = 1;
    textExp.text = "100%";
    textFullExp.text = string.Get(40475);
  else
    sliderExp.maxValue = Calc.GetUpExp(level + 1, turn);
    sliderExp.value = exp - Calc.GetNeedTExp(level, turn);
    textExp.text = string.Concat(math.floor((sliderExp.value / sliderExp.maxValue) * 100), "%");
    textFullExp.text = string.Concat(sliderExp.value, "/", sliderExp.maxValue);
  end
end

function UIStatus.AstrolabeLevelUp()
  astrolabeShowTimer = Astrolabe.levelUpShowTime;
end

function UIStatus.StoreOutfitStyleFromBag(data) --穿時裝成功或失敗
  local result = data:ReadByte();
  if result == 1 then
    Item.SetBagItem(EThings.OutfitStyleInCollection, Item.GetBagCount(EThings.OutfitStyleInCollection) + 1,  itemDatas[outfitStyleIdTempSave], true);
    ShowCenterMessage(string.Concat(itemDatas[outfitStyleIdTempSave]:GetName(true), string.Get(50023)));
    outfitStyleIdTempSave = 0;
  end
  
  UIFrame.SwitchTab(EUIStatusTag.Style);
  currentOutfitSyleCategory = 0;
  currentTag = EUIStatusTag.OutfitStyle;
  this.UpdateUI(true);
end

function UIStatus.CheckOutfitStyleEquiped(itemId)
  local itemSave;

  for i = EItemFitType.OutfitStyle_Head, EItemFitType.OutfitStyle_Boots do
    itemSave = Item.GetBagItem(EThings.Equip, i, 0);
    if itemSave ~= nil and itemDatas[itemSave.Id] ~= nil then
      if itemId == itemSave.Id then
        return true;
      end
    end
  end

  return false;
end

function UIStatus.RefreshBreakthrough()
  if scrollContent_Breakthrough == nil then return end
  if currentTag ~= EUIStatusTag.Breakthrough then return end
  text_BreakthroughLevel.text = Breakthrough.GetBreakthroughLevelText();
  scrollContent_Breakthrough:Refresh();
end

function UIStatus.SetBreakthroughRedDot()
  if not UI.IsVisible(UIStatus) then
    RedDot.Add(ERedDot.Breakthrough);
  end
end

function UIStatus.GetCurrentTag()
  return currentTag;
end

function UIStatus.GetRoleNameAndBarValue()
  return currentRole.name, string.Concat(text_FullHp.text, "\n", text_FullSp.text, "\n", text_Exp.text), currentRole == Role.player;
end

function UIStatus.CheckDataGetUpgradeLvIcon(lv)
  if npcUpgradeDatas[lv] ~= nil then
    return npcUpgradeDatas[lv].UpgradeLvIcon;
  else
    return 0;
  end
end

function UIStatus.ResetAttSetAdd()
  local attSet = Status.GetCurrentAttSet();

  for i = 1, 6 do
    attSetComponents[i].add = attSet.att[i];
  end
end
