EUICollectTag = 
{
  Warrior = 1,
  Style = 2,
  Card = 3,
  CardView = 4,
}

UICollectBook = {};
local this = UICollectBook;

this.name = "UICollectBook";
this.uiController = nil;

local transform_FrameRect;
local currentTag = 0;
local currentIndex = 1;
local rawImage_BG;
local rawImage_Title;
local text_Title;

local tagCount = 4;
local tagObjects = {};
--共用左側頁面
local scrollRect_Obj;
local scrollContent_Obj;
local scrollItems_Obj = {};
local inputField_Search;
local transform_Search;
local event_SearchClear;
local showDatas = {}; --顯示在左側清單列表資料
local text_None;

--武將頁
local warrior_rawImagePosY = 44;
local transform_Warrior;
local currentRoleTurn = 1;
local role_awards = {};
local gameObject_RoleImage;
local rawImage_Role;
local rawImage_RoleGround;
local jmpDrawer_Role;
local rawImage_Gift;
local event_Gift;

local text_RoleName;
local rawImage_WarriorBG;
local image_Element;
local career;
local text_RoleStory;
local text_RoleGainWay;

local event_ChangeRoleL;
local event_ChangeRoleR;
local event_GetAllGift;
local event_NpcInfo;

--造型頁
local player;
local playerData;
local appearence_rawImagePosY = 94;
local transform_Appearence;
local rawImage_PointBG;
local appearence_Items = {};
local text_AppearenceName;
local text_Where;
local text_Point;
local text_AllGet;
local root_AbDetail;
local text_TotalValue;
local scrollContent_PointList;
local scrollItems_PointList = {};
local gameObject_EquipAll;
local event_EquipAll;

--卡片
local transform_Card;
local transform_EquipCard;
local image_Toogle;
local text_Combine;
local event_AddCombine;

local rawImage_CardBG;
local text_CombinationName;
local text_CombinationLevel;
local text_LvFront;
local textGroup_CardValue = {};
local text_CardStory;
local scrollContent_Card;
local scrollItems_Card = {};
local button_CardEquip ={};
local button_CardUpgrate ={};

local bool_Own = false;

--卡片總攬
local currentPage = 1;
local transform_CardView;
local MAX_VIEWCARD = 18;
local rawImage_ViewCards = {};
local text_CardPage;
local event_PageLeft;
local event_PageRight;

--卡片細節
local gameObject_CardDetail;
local rawImage_CardDetail;
local root_CardController;
local cardController;

local bgBrownColor;
local bgWhiteColor;

function UICollectBook.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;

  bgBrownColor = LuaHelper.GetColor(221, 212, 198, 255);
  bgWhiteColor = LuaHelper.GetColor(242, 244, 236, 255);

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  rawImage_BG = uiController:FindRawImage("RawImage_BG");
  rawImage_Title = uiController:FindRawImage("RawImage_Title");
  text_Title = uiController:FindText("Text_Title");

  local event = uiController:FindEvent("Image_Mask");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Mask);

  event = uiController:FindEvent("Image_Close");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Mask);

  event = uiController:FindEvent("Image_Help");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Help);

  local tagNames = {};
  tagNames[1] = string.Get(10403)
  tagNames[2] = string.Get(99685);
  tagNames[3] = string.Get(21418);
  tagNames[4] = string.Get(21419);

  for i = 1, tagCount do 
    tagObjects[i] = {};
    tagObjects[i].gameObject = uiController:FindGameObject(string.Concat("TabObject", i));
    tagObjects[i].rawImage = tagObjects[i].gameObject.transform:Find("RawImage_Tab").gameObject:GetComponent("RawImage");
    tagObjects[i].event = tagObjects[i].rawImage.gameObject:GetComponent("UIEvent");
    tagObjects[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Tag);
    tagObjects[i].event.parameter = i;
    tagObjects[i].text = tagObjects[i].rawImage.transform:Find("Text_Name").gameObject:GetComponent("Text");
    tagObjects[i].text.text = tagNames[i];
    tagObjects[i].redDot = tagObjects[i].text.transform:Find("Image_RedDot").gameObject;
    tagObjects[i].redDot:SetActive(false);
  end

  scrollRect_Obj =  uiController:FindGameObject("ScrollRect_Obj");
  scrollContent_Obj = uiController:FindScrollContent("ScrollContent_Obj");
  scrollContent_Obj.onInitialize = this.OnInitialize_scrollContent_Obj;
  scrollContent_Obj.onItemChange = this.OnItemChange_scrollContent_Obj;
  scrollContent_Obj:Initialize(1);

  transform_Search = uiController:FindGameObject("RootObj_Search");
  event = uiController:FindEvent("Image_Search");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Search);
  inputField_Search = uiController:FindInputField("InputField_Search");
  inputField_Search:AddListener(this.OnInputValueChanged);
  event_SearchClear = uiController:FindEvent("Image_SearchClear");
  event_SearchClear:SetListener(EventTriggerType.PointerClick, this.OnClick_SearchClear);
  text_None = uiController:FindText("Text_None");
  --武將頁
  this.InitWarrior(uiController);
  --外觀頁
  this.InitStyle(uiController);
  --卡片
  this.InitCard(uiController);
end

function UICollectBook.InitWarrior(uiController)
  gameObject_RoleImage = uiController:FindGameObject("GameObject_RoleImage");
  transform_Warrior = uiController:FindGameObject("RootObj_Warrior").transform;
  for i = 1, 5 do
    role_awards[i] = {};
    role_awards[i].gameObject = uiController:FindGameObject(string.Concat("Image_Award", i));
    role_awards[i].item = UIItemController.New(role_awards[i].gameObject.transform);
  end
  
  rawImage_Role = uiController:FindRawImage("RawImage_Role");
  rawImage_RoleGround = uiController:FindRawImage("RawImage_RoleGround");
  jmpDrawer_Role = rawImage_Role.gameObject:AddComponent(typeof(JmpDrawer));

  text_RoleName = uiController:FindText("Text_RoleName");
  rawImage_WarriorBG = uiController:FindRawImage("Image_WarriorBG");
  
  rawImage_Gift = uiController:FindRawImage("RawImage_Gift").gameObject:GetComponent("UIRawImage");
  event_Gift = rawImage_Gift.gameObject:GetComponent("UIEvent");
  event_Gift:SetListener(EventTriggerType.PointerClick, this.OnClick_GetReward); 

  image_Element = uiController:FindGameObject("Image_Element");
  career = UICareerController.New(image_Element, 3);
  text_RoleStory = uiController:FindText("Text_RoleStory");
  text_RoleGainWay = uiController:FindText("Text_RoleGainWay");

  event_ChangeRoleL = uiController:FindEvent("Image_ChangeRoleL");
  event_ChangeRoleL:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeRoleL);

  event_ChangeRoleR = uiController:FindEvent("Image_ChangeRoleR");
  event_ChangeRoleR:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeRoleR);

  event_GetAllGift = uiController:FindEvent("Image_GetAllGift");
  event_GetAllGift:SetListener(EventTriggerType.PointerClick, this.OnClick_GetAllGift);

  event_NpcInfo = uiController:FindEvent("Image_NpcInfo");
  event_NpcInfo:SetListener(EventTriggerType.PointerClick, this.OnClick_NpcInfo);
end

function UICollectBook.InitStyle(uiController)
  transform_Appearence = uiController:FindGameObject("RootObj_Appearence");  
  for i =1, 5 do 
    appearence_Items[i] = {};
    appearence_Items[i].gameObject = uiController:FindGameObject(string.Concat("ItemObj", i));
    appearence_Items[i].image_BG = appearence_Items[i].gameObject.transform:Find("Image_BG").gameObject:GetComponent("Image");
    appearence_Items[i].rawImage = appearence_Items[i].gameObject.transform:Find("Image").gameObject:GetComponent("RawImage");
    appearence_Items[i].text_Score = appearence_Items[i].gameObject.transform:Find("Text_Score").gameObject:GetComponent("Text");
    appearence_Items[i].gameObject_RedDot = appearence_Items[i].gameObject.transform:Find("Image_RedDot").gameObject;    
    appearence_Items[i].event = appearence_Items[i].rawImage.gameObject:GetComponent("UIEvent");
    appearence_Items[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_AppearenceItem);
    appearence_Items[i].event.parameter = i;
  end  
  rawImage_PointBG = uiController:FindRawImage("RawImage_PointBG")
  text_AppearenceName = uiController:FindText("Text_AppearenceName"); 
  text_Where = uiController:FindText("Text_Where");
  text_Point = uiController:FindText("Text_Point");
  text_AllGet = uiController:FindText("Text_AllGet");

  text_TotalValue = {};
  for i = 1, 6 do 
    text_TotalValue[i] = uiController:FindText(string.Concat("Text_V", i));
  end
  gameObject_EquipAll = uiController:FindGameObject("Image_EquipAll")
  event_EquipAll = uiController:FindEvent("Image_EquipAll")
  event_EquipAll:SetListener(EventTriggerType.PointerClick, this.OnClick_EquipAll);
  
  scrollContent_PointList = uiController:FindScrollContent("ScrollContent_PointList");
  scrollContent_PointList.onInitialize = this.OnInitialize_scrollContent_PointList;
  scrollContent_PointList.onItemChange = this.OnItemChange_scrollContent_PointList;
  scrollContent_PointList:Initialize(1);  
  
  root_AbDetail = uiController:FindGameObject("Root_AbDetail");
end

function UICollectBook.InitCard(uiController)
  transform_Card = uiController:FindGameObject("RootObj_Card").transform;
  transform_EquipCard = uiController:FindGameObject("RootObj_EquipCard").transform;

  image_Toogle = uiController:FindImage("Image_Toogle");
  local event = image_Toogle.gameObject:GetComponent("UIEvent");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Toogle);
  text_Combine = uiController:FindText("Text_Combine");
  event_AddCombine = uiController:FindEvent("Image_AddCombine");
  event_AddCombine:SetListener(EventTriggerType.PointerClick, this.OnClick_AddCombine);

  rawImage_CardBG = uiController:FindRawImage("RawImage_CardBG");
  text_CombinationName = uiController:FindText("Text_CombinationName");
  text_CombinationLevel = uiController:FindText("Text_CombinationLevel");
  text_LvFront = uiController:FindText("Text_LvFront");
  for i = 1, 6 do 
    textGroup_CardValue[i] = uiController:FindText(string.Concat("Text_CardValue", i));
  end
  text_CardStory = uiController:FindText("Text_CardStory");

  scrollContent_Card = uiController:FindScrollContent("ScrollContent_Card");
  scrollContent_Card.onInitialize = this.OnInitialize_scrollContent_Card;
  scrollContent_Card.onItemChange = this.OnItemChange_scrollContent_Card;
  scrollContent_Card:Initialize(1);

  button_CardEquip.event = uiController:FindEvent("Image_CardEquip");
  button_CardEquip.event:SetListener(EventTriggerType.PointerClick, this.OnClick_CardEquip);
  button_CardEquip.text = button_CardEquip.event.transform:Find("Text").gameObject:GetComponent("Text");
  button_CardEquip.image = button_CardEquip.event.gameObject:GetComponent("Image");

  button_CardUpgrate.event = uiController:FindEvent("Image_CardUpgrate");
  button_CardUpgrate.event:SetListener(EventTriggerType.PointerClick, this.OnClick_CardUpgrate);
  button_CardUpgrate.text = button_CardUpgrate.event.transform:Find("Text").gameObject:GetComponent("Text");
  button_CardUpgrate.image = button_CardUpgrate.event.gameObject:GetComponent("Image");

  --CardView
  transform_CardView = uiController:FindGameObject("RootObj_CardView").transform;
  for i = 1, MAX_VIEWCARD do 
    rawImage_ViewCards[i] = {};
    rawImage_ViewCards[i].root = uiController:FindGameObject(string.Concat("Card", i));
    rawImage_ViewCards[i].controller = UICardSmallController.New(rawImage_ViewCards[i].root.transform);
  end     
  event_PageLeft = uiController:FindEvent("Image_CardLeftPage");
  event_PageLeft:SetListener(EventTriggerType.PointerClick, this.OnClick_CardLeftPage);

  event_PageRight = uiController:FindEvent("Image_CardRightPage");
  event_PageRight:SetListener(EventTriggerType.PointerClick, this.OnClick_CardRightPage);

  text_CardPage = uiController:FindText("Text_CardPage");

  gameObject_CardDetail = uiController:FindGameObject("Root_CardDetail");   
  root_CardController = uiController:FindGameObject("Root_CardController");
  cardController = UICardController.New(root_CardController.transform);
  event = uiController:FindEvent("Image_CardMask");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_CardMask);

  event = uiController:FindEvent("Image_AllAb");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_AllAb);
end

function UICollectBook.LoadBG()
  TextureManager.SetPng(ETextureUseType.UI, "common0_bod051", rawImage_BG);
  -- TextureManager.SetPng(ETextureUseType.UI, "common0_bod067", rawImage_Title);  
end

function UICollectBook.OnOpen(tag)
  this.CheckAllRedDot();
  this.LoadBG();
  if tag == nil then 
    tag = EUICollectTag.Warrior;
  end
  this.ChangeTags(tag);    
  return true;
end

function UICollectBook.UpdateTags(tagClick)
  text_Title.text = tagObjects[tagClick].text.text;
  for i = 1, tagCount do 
    if tagClick == i then 
      TextureManager.SetPng(ETextureUseType.UI, "common0_kin004L", tagObjects[i].rawImage);
    else
      TextureManager.SetPng(ETextureUseType.UI, "common0_kin004D", tagObjects[i].rawImage);
    end
  end
end

function UICollectBook.OnClose()
  currentTag = 0;
  currentIndex = 1;
  currentRoleTurn = 1;
  bool_Own = false;
  inputField_Search.text = "";
  image_Toogle.sprite = UI.GetCheckBoxSprite(bool_Own);
  if player ~= nil then
    player:Destroy();
    player = nil;
  end
  jmpDrawer_Role:Disable();
end

function UICollectBook.CheckTeach()
  if currentTag == EUICollectTag.Warrior then
    UITeach.Guide(17);
  elseif currentTag == EUICollectTag.Style then
    UITeach.Guide(18);
  elseif currentTag == EUICollectTag.Card then
    UITeach.Guide(19);
  end
end

function UICollectBook.OnClick_Mask(event)
  UI.Close(UICollectBook);
end

function UICollectBook.OnClick_Help(event)
  UI.Open(UITutorialHint, 31, this);
end

function UICollectBook.OnInitialize_scrollContent_Obj(scrollItems)  
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Obj[itemIndex] = {};
    scrollItems_Obj[itemIndex].image_BG = scrollItems[itemIndex]:Find("Image_BG"):GetComponent("RawImage");
    scrollItems_Obj[itemIndex].image_Icon = scrollItems[itemIndex]:Find("Image_Icon"):GetComponent("Image");
    scrollItems_Obj[itemIndex].image_Icon2 = scrollItems[itemIndex]:Find("Image_Icon2"):GetComponent("Image");
    scrollItems_Obj[itemIndex].name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");    

    scrollItems_Obj[itemIndex].gameObject_Warrior = scrollItems[itemIndex]:Find("GameObject_Warrior").gameObject;
    scrollItems_Obj[itemIndex].getStatus = {};
    scrollItems_Obj[itemIndex].getEvent = {};
    for i = 1, 3 do
      scrollItems_Obj[itemIndex].getStatus[i] = scrollItems_Obj[itemIndex].gameObject_Warrior.transform:Find(string.Concat("Image", i)):GetComponent("Image");
      scrollItems_Obj[itemIndex].getEvent[i] = scrollItems_Obj[itemIndex].gameObject_Warrior.transform:Find(string.Concat("Image", i)):GetComponent("UIEvent");
      scrollItems_Obj[itemIndex].getEvent[i]:SetListener(EventTriggerType.PointerClick, this.OnClick_WarriorStatus);  
    end

    scrollItems_Obj[itemIndex].gameObject_Element = scrollItems[itemIndex]:Find("Image_Element").gameObject;
    scrollItems_Obj[itemIndex].gameObject_Career = UICareerController.New(scrollItems_Obj[itemIndex].gameObject_Element, 4);

    scrollItems_Obj[itemIndex].event = scrollItems[itemIndex]:Find("Image_BG"):GetComponent("UIEvent");
    scrollItems_Obj[itemIndex].event:SetListener(EventTriggerType.PointerClick, this.OnClick_ScrollBG);

    scrollItems_Obj[itemIndex].gameObject_Lv = scrollItems[itemIndex]:Find("GameObject_Lv").gameObject;
    scrollItems_Obj[itemIndex].text_Lv = scrollItems_Obj[itemIndex].gameObject_Lv.transform:Find("Text_Lv"):GetComponent("Text");    
    scrollItems_Obj[itemIndex].image_SelectBut = scrollItems[itemIndex]:Find("Image_SelectBut"):GetComponent("Image");
  end
end

function UICollectBook.OnItemChange_scrollContent_Obj(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local data = showDatas[index];  
  if data == nil then
    return false;
  end

  TextureManager.SetPng(ETextureUseType.UI, "common0_bod068", scrollItems_Obj[itemIndex].image_BG);
  if itemIndex % 2 == 0 then 
    LuaHelper.SetColor(scrollItems_Obj[itemIndex].image_BG, bgBrownColor);
  else
    LuaHelper.SetColor(scrollItems_Obj[itemIndex].image_BG, bgWhiteColor);
  end

  scrollItems_Obj[itemIndex].image_SelectBut.gameObject:SetActive(index == currentIndex);
  scrollItems_Obj[itemIndex].gameObject_Lv:SetActive(currentTag == EUICollectTag.Card);
  if currentTag == EUICollectTag.Warrior then     
    scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(false);
    scrollItems_Obj[itemIndex].gameObject_Warrior:SetActive(true);
    scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(false);
    for i = 1, 3 do       
      local isExsist = data.ids[i] ~= 0;      
      scrollItems_Obj[itemIndex].getStatus[i].gameObject:SetActive(isExsist);
      if isExsist then 
        local warriorData = collectWarriorDatas[data.ids[i]];
        
        if warriorData ~= nil then
          local isOwn = BitFlag.Get(warriorData.getFlag);      
          local isGetReward = BitFlag.Get(warriorData.flag);

          scrollItems_Obj[itemIndex].getEvent[i].parameter = {id = warriorData.id, turn = i, dataIndex = index, bg = scrollItems_Obj[itemIndex].image_BG.transform};
          
          if isOwn and not isGetReward then --擁有且沒領過獎
            --scrollItems_Obj[itemIndex].getEvent[i].parameter = {id = warriorData.id, turn = i, dataIndex = index, bg = scrollItems_Obj[itemIndex].image_BG.transform};
            scrollItems_Obj[itemIndex].getStatus[i].sprite = UI.GetSprite("com119");
          elseif isOwn and isGetReward then --領過獎
            --scrollItems_Obj[itemIndex].getEvent[i].parameter = {id = nil, turn = i, dataIndex = index, bg = scrollItems_Obj[itemIndex].image_BG.transform};
            scrollItems_Obj[itemIndex].getStatus[i].sprite = UI.GetSprite("com115");
          elseif not isOwn then 
            --scrollItems_Obj[itemIndex].getEvent[i].parameter = {id = nil, turn = i, dataIndex = index, bg = scrollItems_Obj[itemIndex].image_BG.transform};
            scrollItems_Obj[itemIndex].getStatus[i].sprite = UI.GetSprite("com113");
          end
        end
      end
    end
    scrollItems_Obj[itemIndex].gameObject_Element:SetActive(true);
    scrollItems_Obj[itemIndex].gameObject_Career:SetCareer(npcDatas[data.ids[1]].turn, npcDatas[data.ids[1]].element, ECareer.None, true);
    scrollItems_Obj[itemIndex].name.text =npcDatas[data.ids[1]].name;
  elseif currentTag == EUICollectTag.Style then 
    scrollItems_Obj[itemIndex].gameObject_Element:SetActive(false);
    scrollItems_Obj[itemIndex].gameObject_Warrior:SetActive(false);
    scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(false);
    scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(false);
    local equipCount , count = CollectStyle.GetCollectCount(data.id);
    scrollItems_Obj[itemIndex].name.text = string.Concat(string.Get(data.name), " (", equipCount, "/", count, ")") ;
    --判斷是否有可以放入
    for k, v in pairs(data.itemId)do
      if not CollectStyle.CheckEquiped(data.id, k) then         
        if Item.GetItemCount(v) > 0 then 
          scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(true);
          scrollItems_Obj[itemIndex].image_Icon.sprite = UI.GetSprite("com118");
          break;
        end
      end
    end
  elseif currentTag == EUICollectTag.Card then 
    scrollItems_Obj[itemIndex].gameObject_Element:SetActive(false);    
    scrollItems_Obj[itemIndex].gameObject_Warrior:SetActive(false);

    --todo 判斷有無解鎖
    if CollectCard.GetLv(data.id) == 0 then 
      scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(false);
      scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(true);    
      scrollItems_Obj[itemIndex].gameObject_Lv:SetActive(false);
      if CollectCard.CheckCanLevelUp(data.id) then        
        scrollItems_Obj[itemIndex].image_Icon.sprite = UI.GetSprite("com112");
      else
        scrollItems_Obj[itemIndex].image_Icon.sprite = UI.GetSprite("com117");
      end 
    else
      scrollItems_Obj[itemIndex].text_Lv.text = CollectCard.GetLv(data.id);    
      if CollectCard.CheckCanLevelUp(data.id) then 
        scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(true);
        scrollItems_Obj[itemIndex].image_Icon.sprite = UI.GetSprite("com118");
        if CollectCard.IsEquiped(data.id) then 
          scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(true);               
          scrollItems_Obj[itemIndex].image_Icon2.sprite = UI.GetSprite("com033");
        else
          scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(false);
        end
      else
        scrollItems_Obj[itemIndex].image_Icon2.gameObject:SetActive(false);
        if CollectCard.IsEquiped(data.id) then 
          scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(true);
          scrollItems_Obj[itemIndex].image_Icon.sprite = UI.GetSprite("com033");
        else
          scrollItems_Obj[itemIndex].image_Icon.gameObject:SetActive(false);    
        end
      end
    end

    scrollItems_Obj[itemIndex].name.text = string.Get(data.name);
  end

  local temp = {};
  temp.index = index;
  temp.bg = scrollItems_Obj[itemIndex].image_BG.transform;
  
  scrollItems_Obj[itemIndex].event.parameter = temp;
  return true;
end

function UICollectBook.OnInitialize_scrollContent_PointList(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_PointList[itemIndex] = {};
    scrollItems_PointList[itemIndex].text_Point = scrollItems[itemIndex]:Find("Text_Point"):GetComponent("Text");
    scrollItems_PointList[itemIndex].text_V = {};
    for i = 1, 4 do
      scrollItems_PointList[itemIndex].text_V[i] = scrollItems[itemIndex]:Find("Text_V"..i):GetComponent("Text");
    end
  end
end

function UICollectBook.OnItemChange_scrollContent_PointList(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local data = collectStyleValueDatas[index];
  if data == nil then
    return false;
  end
  
  local isOpen = CollectStyle.GetNowPoint() >= data.score;
  for i = 1, 4 do
    if data.attribute[i].kind ~= 0 then
      scrollItems_PointList[itemIndex].text_V[i].text = string.Concat(this.GetAttributeString(data.attribute[i].kind),data.attribute[i].value);
      if isOpen then
        LuaHelper.SetColor(scrollItems_PointList[itemIndex].text_V[i], Color.LightGreen);
      else
        LuaHelper.SetColor(scrollItems_PointList[itemIndex].text_V[i], Color.Black);
      end
    else
      scrollItems_PointList[itemIndex].text_V[i].text = "";
    end
  end
  scrollItems_PointList[itemIndex].text_Point.text = string.Concat(string.Get(21132), ":", data.score);
  return true;
end
  
function UICollectBook.GetAttributeString(kind)
    if kind == EAttribute.Hp then
      return string.Get(20033);
    elseif kind == EAttribute.Sp then
      return string.Get(20034);
    elseif kind == EAttribute.Int then
      return string.Get(20037);
    elseif kind == EAttribute.Atk then
      return string.Get(20038);
    elseif kind == EAttribute.Def then
      return string.Get(20039);
    elseif kind == EAttribute.Agi then
      return string.Get(20042);
    elseif kind == EAttribute.Hpx then
      return string.Get(20040);
    elseif kind == EAttribute.Spx then
      return string.Get(20041)
    else
      return kind;
    end
  end

function UICollectBook.OnClick_WarriorStatus(event)
  -- if event.parameter.id ~= nil then 
  --   sendBuffer:Clear();
  --   sendBuffer:WriteUInt16(event.parameter.id);  
  --   Network.Send(95, 1, sendBuffer);
  -- end  
  if event.parameter.turn ~= currentRoleTurn or currentIndex ~= event.parameter.dataIndex then 
    local id = showDatas[event.parameter.dataIndex].ids[event.parameter.turn];
    local data = collectWarriorDatas[id];

    currentRoleTurn = event.parameter.turn;  
    currentIndex = event.parameter.dataIndex;
    this.UpdateInfo(); 
    --[[
    if BitFlag.Get(data.getFlag) and not BitFlag.Get(data.flag) then --擁有沒領獎
      currentRoleTurn = event.parameter.turn;  
      currentIndex = event.parameter.dataIndex;
      this.UpdateInfo(); 
    end
    --]]   
  end
end

function UICollectBook.OnClick_ScrollBG(event)
  local result = event.parameter;
  if showDatas[result.index] ~= nil then 
    currentIndex = result.index;
    currentRoleTurn = 1;
    this.UpdateInfo();    
  end
end

function UICollectBook.OnClick_Tag(event)  
  this.ChangeTags(event.parameter);
  return true;
end

function UICollectBook.ChangeTags(mainTag)
  if currentTag == mainTag then 
    return 
  end
  currentTag = mainTag;
  this.UpdateTags(mainTag);   
  if currentTag == EUICollectTag.Warrior then 
    scrollRect_Obj:SetActive(true);
    rawImage_RoleGround.gameObject:SetActive(true);
    rawImage_RoleGround.transform.localPosition = Vector3.New(rawImage_RoleGround.transform.localPosition.x, warrior_rawImagePosY, 0);        
  elseif currentTag == EUICollectTag.Style then 
    scrollRect_Obj:SetActive(true);
    rawImage_RoleGround.gameObject:SetActive(true);
    rawImage_RoleGround.transform.localPosition = Vector3.New(rawImage_RoleGround.transform.localPosition.x, appearence_rawImagePosY, 0);    
  elseif currentTag == EUICollectTag.Card then 
    scrollRect_Obj:SetActive(true);
    rawImage_RoleGround.gameObject:SetActive(false);
  else 
    scrollRect_Obj:SetActive(false);
    rawImage_RoleGround.gameObject:SetActive(false);
  end
  
  transform_Search.gameObject:SetActive(currentTag ~= EUICollectTag.Card);
  inputField_Search.text = "";
  event_SearchClear.gameObject:SetActive(inputField_Search.text ~= "");
  transform_EquipCard.gameObject:SetActive(currentTag == EUICollectTag.Card);
  
  gameObject_EquipAll:SetActive(currentTag == EUICollectTag.Style);
  transform_Appearence.gameObject:SetActive(currentTag == EUICollectTag.Style);
  transform_Warrior.gameObject:SetActive(currentTag == EUICollectTag.Warrior);
  transform_Card.gameObject:SetActive(currentTag == EUICollectTag.Card);
  transform_CardView.gameObject:SetActive(currentTag == EUICollectTag.CardView);
  
  gameObject_CardDetail:SetActive(false);
  root_AbDetail.gameObject:SetActive(false);
  this.SetShowDatas();
  this.CheckTeach();
end

function UICollectBook.OnClick_Close()
  UI.Close(this);
end

function UICollectBook.SetShowDatas(searchName)
  table.Clear(showDatas);
  text_None.gameObject:SetActive(false);
  if currentTag == EUICollectTag.Warrior then 
    for k, v in pairs(collectWarriorSortDatas)do
      table.insert(showDatas, v);              
    end    

    table.sort(showDatas, this.SortWarriorData);
  elseif currentTag == EUICollectTag.Style then 
    for k, v in pairs(collectStyleDatas)do 
      table.insert(showDatas, v);
    end
  elseif currentTag == EUICollectTag.Card then     
    for k, v in pairs(collectCardDatas)do 
      if bool_Own == false or CollectCard.GetLv(v.id) > 0 then 
        table.insert(showDatas, v);        
      end
    end
  elseif currentTag == EUICollectTag.CardView then 
    for k, v in pairs(collectCardSortDatas)do       
      table.insert(showDatas, v);      
    end

    -- table.sort(showDatas, this.SortViewCard); 
  end

  --search part
  if searchName ~= nil then 
    local deleteTable = {};
    for k, v in pairs(showDatas)do
      local name = "";
      if currentTag == EUICollectTag.Warrior then 
        name = npcDatas[v.ids[1]].name;
      elseif currentTag == EUICollectTag.CardView then 
        name = npcDatas[v.warriorId].name;
      else
        name = string.Get(v.name);        
      end
              
      if string.find(name, searchName, 1) == nil then         
        table.insert( deleteTable, v);        
      end
    end
    for k, v in pairs(deleteTable) do
      table.RemoveByValue(showDatas, v);
    end
  end

  currentIndex = 1;
  currentPage = 1;
  if currentTag ~= EUICollectTag.CardView then 
    if currentTag == EUICollectTag.Warrior and table.Count(showDatas) > 0 then 
      currentRoleTurn = 1;
    end
    scrollContent_Obj:Reset(table.Count(showDatas));    
    if table.Count(showDatas) == 0 and currentTag == EUICollectTag.Card then 
      text_None.gameObject:SetActive(true)
    end
  end  
  this.UpdateInfo();
end

function UICollectBook.OnClick_Search()
  currentPage = 1;
  this.SetShowDatas(inputField_Search.text);
end

function UICollectBook.OnClick_SearchClear()  
  currentPage = 1;
  inputField_Search.text = "";
  this.SetShowDatas();
end

function UICollectBook.OnInputValueChanged()
  event_SearchClear.gameObject:SetActive(inputField_Search.text ~= "");
end

function UICollectBook.UpdateInfo()
  this.UpdateRedDot();
  local data = showDatas[currentIndex];
  if currentTag ==  EUICollectTag.Warrior then 
    this.UpdateRoleInfo(data);    
  elseif currentTag == EUICollectTag.Style then 
    this.UpdateAppearenceInfo(data);
  elseif currentTag == EUICollectTag.Card then 
    this.UpdateCardInfo(data);
  elseif currentTag == EUICollectTag.CardView then 
    this.UpdateCardView();
  end  
  scrollContent_Obj:Refresh();
end

function UICollectBook.UpdateList()
  if currentTag ==  EUICollectTag.Warrior then
    table.sort(showDatas, this.SortWarriorData);
  elseif currentTag == EUICollectTag.CardView then 
    table.sort(showDatas, this.SortViewCard); 
  end  
  this.UpdateInfo();
end

--武將頁
function UICollectBook.UpdateChangeRoleButton()  
  event_ChangeRoleL.gameObject:SetActive(currentRoleTurn ~= 1);
  
  local data = showDatas[currentIndex];
  if data ~= nil then 
    local isNext = data.ids[currentRoleTurn + 1] ~= nil and data.ids[currentRoleTurn + 1] ~= 0;
    
    event_ChangeRoleR.gameObject:SetActive(isNext);
  else
    event_ChangeRoleR.gameObject:SetActive(false);
  end
end

function UICollectBook.OnClick_ChangeRoleL()  
  if currentRoleTurn == 1 then return end
  currentRoleTurn = currentRoleTurn -1;
  this.UpdateInfo();
end

function UICollectBook.OnClick_ChangeRoleR()
  local data = showDatas[currentIndex];
  local isNext = data.ids[currentRoleTurn + 1] ~= nil and data.ids[currentRoleTurn + 1] ~= 0;
  if isNext == false then return end
  currentRoleTurn = currentRoleTurn +1;
  this.UpdateInfo();
end

function UICollectBook.OnClick_GetAllGift()
  local canGetGift = {};
  local cWarriorData = nil;
  for k, v in pairs(collectWarriorSortDatas)do 
    for i = 1, 3 do 
      if v.ids[i] ~= nil and v.ids[i] ~= 0 then 
        cWarriorData = collectWarriorDatas[v.ids[i]];
        if BitFlag.Get(cWarriorData.getFlag) and not BitFlag.Get(cWarriorData.flag) then --擁有沒領獎
          table.insert(canGetGift, v.ids[i]);
        end
      end
    end
  end
  
  if table.Count(canGetGift) <= 0 then return end

  sendBuffer:Clear();
  sendBuffer:WriteByte(table.Count(canGetGift));
  for k, v in pairs(canGetGift)do 
    sendBuffer:WriteUInt16(v);    
  end  
  Network.Send(95, 11, sendBuffer);
end

function UICollectBook.OnClick_NpcInfo(uiEvent)
  if uiEvent.parameter == nil then return end

  UI.Open(UINpcInfo, uiEvent.parameter);
end

function UICollectBook.OnClick_GetReward()
  local data = showDatas[currentIndex];
  if data == nil then 
    return 
  end

  local id = data.ids[currentRoleTurn];
  if id == 0 or id == nil then 
    return 
  end
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);  
  Network.Send(95, 1, sendBuffer);
end

function UICollectBook.UpdateRoleInfo(data)  
  if data ~= nil then 
    TextureManager.SetPng(ETextureUseType.UI, "common0_com040", rawImage_RoleGround);
    -- TextureManager.SetPng(ETextureUseType.UI, "common0_bod070", rawImage_WarriorBG);   
    TextureManager.SetPng(ETextureUseType.UI, "Ani_Reward", rawImage_Gift);        
    local id = data.ids[currentRoleTurn];
    
    local nowWarriorData = collectWarriorDatas[id];
    if npcDatas[data.ids[1]] ~= nil then 
      text_RoleGainWay.text = string.GetMoral(npcDatas[data.ids[1]].moral, false);
    else
      text_RoleGainWay.text = string.GetMoral(0);
    end

    if npcDatas[id] ~= nil then
      local npcData = npcDatas[id];
      local offsetX, offsetY = npcData:GetOffset();

      rawImage_Role.gameObject:SetActive(true);        

      jmpDrawer_Role:GenerateAndDraw(npcData:GetAtlasParams(ERolePose.Prepare + 1));
      jmpDrawer_Role.offsetY = offsetY;
      
      text_RoleName.text = npcData.name;
      career:SetCareer(npcData.turn, npcData.element, ECareer.None, true);
    else      
      rawImage_Role.gameObject:SetActive(false);        
    end

    local giftActive = false;
    local giftGot = false;
    if nowWarriorData ~= nil then 
      if BitFlag.Get(nowWarriorData.getFlag) then 
        LuaHelper.SetColor(rawImage_Role, Color.White);   
        if not BitFlag.Get(nowWarriorData.flag) then --還沒領過獎
          giftActive = true;
        else
          giftGot = true;
        end

        event_NpcInfo.gameObject:SetActive(true);
        event_NpcInfo.parameter = nowWarriorData.id
      else
        LuaHelper.SetColor(rawImage_Role, Color.Black);

        if Define.SuperAI then
          event_NpcInfo.gameObject:SetActive(true);
          event_NpcInfo.parameter = nowWarriorData.id
        else
          event_NpcInfo.gameObject:SetActive(false);
          event_NpcInfo.parameter = nil;
        end
      end

      if giftGot then 
        rawImage_Gift.gameObject:SetActive(false);
      else
        rawImage_Gift.gameObject:SetActive(true);
        if giftActive then 
          LuaHelper.SetColor(rawImage_Gift, Color.White);
          rawImage_Gift.shiftUV = true;
          event_Gift.enabled = true;
        else
          LuaHelper.SetColor(rawImage_Gift, Color.Gray);
          rawImage_Gift.shiftUV = false;
          event_Gift.enabled = false;
        end
      end

      text_RoleStory.text = nowWarriorData.storyStr;
      --text_RoleGainWay.text = string.Get(nowWarriorData.gainStr);      

      for i = 1, 5 do
        if nowWarriorData.award[i].itemId ~= 0 and itemDatas[nowWarriorData.award[i].itemId] ~= nil then 
          role_awards[i].gameObject:SetActive(true);     
          local itemData = itemDatas[nowWarriorData.award[i].itemId];
          local stringCount = "";
          if itemData ~= nil then 
            local count = nowWarriorData.award[i].count;            
            if itemData:IsDirectUse() then            
              count = itemData.attribute[1].value * nowWarriorData.award[i].count;
            end
            stringCount = Item.GetSimpleValue(count, true)
          end     
          role_awards[i].item:SetItem(nowWarriorData.award[i].itemId, nowWarriorData.award[i].count, nil, true);
          role_awards[i].item.text_Quant.text = stringCount;
          role_awards[i].item:SetEvent(this.OnClickAward, nowWarriorData.award[i].itemId);    
        else
          role_awards[i].gameObject:SetActive(false);
        end
      end
    end
  end
  this.UpdateGetllAllButton();
  this.UpdateChangeRoleButton();  
end

function UICollectBook.UpdateGetllAllButton()
  local cWarriorData = nil;
  for k, v in pairs(collectWarriorSortDatas)do 
    for i = 1, 3 do 
      if v.ids[i] ~= nil and v.ids[i] ~= 0 then 
        cWarriorData = collectWarriorDatas[v.ids[i]];
        if BitFlag.Get(cWarriorData.getFlag) and not BitFlag.Get(cWarriorData.flag) then --擁有沒領獎
          event_GetAllGift.gameObject:SetActive(true);
          return;
        end
      end
    end
  end
  event_GetAllGift.gameObject:SetActive(false);
end

function UICollectBook.OnClickAward(event)
  UIItemInfo.Show(event.parameter);
end

--外觀頁
function UICollectBook.UpdateAppearenceInfo(data)
  if data == nil then return end
  TextureManager.SetPng(ETextureUseType.UI, "common0_com040", rawImage_RoleGround);
  -- TextureManager.SetPng(ETextureUseType.UI, "common0_bod070", rawImage_PointBG);  
  --預覽模型 
  playerData = PlayerData.Copy(Role.player.data);
  local OutfitStyleGap = 5;
  local count = 0;
  for i = 1, 5 do
    local itemId = data.itemId[i];
    if itemId ~= 0 then
      appearence_Items[i].gameObject:SetActive(true);
      playerData.equips[itemDatas[itemId].fitType + OutfitStyleGap] = 0;
      playerData.equips[itemDatas[itemId].fitType] = itemId;
      itemDatas[itemId]:SetIcon(appearence_Items[i].rawImage, ETextureUseType.UI);
      appearence_Items[i].text_Score.text = string.Concat(string.Get(21132), ":",data.itemScore[i]);
      appearence_Items[i].gameObject_RedDot:SetActive(false);
      if CollectStyle.CheckEquiped(data.id, i) then
        count = count + 1;
        LuaHelper.SetColor(appearence_Items[i].rawImage, Color.White);
        LuaHelper.SetColor(appearence_Items[i].image_BG, Color.White);
        LuaHelper.SetColor(appearence_Items[i].text_Score, Color.LightBlue);
      else
        LuaHelper.SetColor(appearence_Items[i].rawImage, Color.Gray);
        LuaHelper.SetColor(appearence_Items[i].image_BG, Color.Gray);
        LuaHelper.SetColor(appearence_Items[i].text_Score, Color.Gray);
        if Item.GetItemCount(itemId) > 0 then
          appearence_Items[i].gameObject_RedDot:SetActive(true);
        end
      end
    else
      appearence_Items[i].gameObject:SetActive(false);
    end
  end
  
  if count ~= 0 then
    gameObject_EquipAll:SetActive(true);
  else
    gameObject_EquipAll:SetActive(false);
  end
  
  LuaHelper.SetColor(rawImage_Role, Color.White);
  jmpDrawer_Role:GenerateAndDraw(playerData:GetAtlasParams(0, ERolePose.Prepare + 1));
  jmpDrawer_Role.offsetY = 0;
  
  text_AppearenceName.text = string.Get(data.name);
  text_Where.text = string.Concat(string.Get(21405), ":", string.Get(data.info));
  text_Point.text = string.Concat(string.Get(21407), ":", CollectStyle.GetNowPoint());
  text_AllGet.text = string.Concat(string.Get(21423), ":", data.itemScore[6]);
  
  local allAb = CollectStyle.GetAllAb();
  local i = 1;
  for k, v in pairs(text_TotalValue) do 
    v.text = "";
  end
  for k, v in pairs(allAb) do 
    if i > 6 then 
      break;
    end    
    text_TotalValue[i].text = string.Concat(this.GetAttributeString(k), "+", v);
    i = i + 1;
  end  
end

function UICollectBook.OnClick_AllAb(event)
  if root_AbDetail.gameObject.activeInHierarchy then 
    root_AbDetail.gameObject:SetActive(false);
  else
    root_AbDetail.gameObject:SetActive(true);
    scrollContent_PointList:Reset(table.maxn(collectStyleValueDatas));
  end
end

function UICollectBook.OnClick_AppearenceItem(event)
  local index = event.parameter;
  local showData = showDatas[currentIndex];
  
  local text = nil;
  local func = nil;
  local equipText = nil;
  local equipFunc = nil;
  
  --取出
  if CollectStyle.CheckEquiped(showData.id, index) then
    text = string.Get(21413);
    func = function()
      CollectStyle.SendTakeStyleOut(showData.id, index);
    end
    
    if UIStatus.CheckOutfitStyleEquiped(CollectStyle.GetItemId(showData.id, index)) then
      equipText = string.Get(98003);
      equipFunc =  function()
        CollectStyle.SendUnEquipOutfitStyle(index);
      end
    else
      equipText = string.Get(20165);
      equipFunc = function()
        CollectStyle.SendEquipOutfitStyle(showData.id, index);
      end
    end
    
  --放入
  elseif Item.GetItemCount(showData.itemId[index]) > 0 then 
    text = string.Get(21408);
    func = function()
      CollectStyle.SendStoreStyle(showData.id, index);
    end
  end

  UI.Open(UIItemInfo, EThings.None, showData.itemId[index], 1, text, func, equipText, equipFunc);
end

function UICollectBook.OnClick_EquipAll()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  local count = 0;
  for i = 1, 5 do
    local itemId = showDatas[currentIndex].itemId[i];
    if itemId ~= 0 then
      if CollectStyle.CheckEquiped(showDatas[currentIndex].id, i) and not UIStatus.CheckOutfitStyleEquiped(CollectStyle.GetItemId(showDatas[currentIndex].id, i)) then
        count = count + 1;
      end
    end
  end
  
  --C:023-127 <收藏策一鍵穿顯示時裝外觀>+數量(1) +<< 收集冊ID(2) + 部位索引(1)>>
  if count ~= 0 then
    sendBuffer:Clear();
    sendBuffer:WriteUInt16(showDatas[currentIndex].id);
    Network.Send(23, 127, sendBuffer);
  else
    ShowCenterMessage(string.Get(23675));
  end
  
end

--卡片
function UICollectBook.OnInitialize_scrollContent_Card(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Card[itemIndex] = {};
    scrollItems_Card[itemIndex].cardSmallController = UICardSmallController.New(scrollItems[itemIndex]:Find("Root_CardController"));
    scrollItems_Card[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");    
    scrollItems_Card[itemIndex].text_Count = scrollItems[itemIndex]:Find("Text_Count"):GetComponent("Text");
  end
end

function UICollectBook.OnItemChange_scrollContent_Card(dataIndex, itemIndex)
  local id = showDatas[currentIndex].card[dataIndex + 1];
  
  if id == nil or id == 0 then return false end
  
  local data = collectCardSortDatas[id];
  local warriorData = npcDatas[data.warriorId];
  
  scrollItems_Card[itemIndex].cardSmallController:SetData(id, nil, true, false);

  local name = warriorData.name;
  if Define.IsDebugMode() then
    name = string.Concat(name, "(", data.roleCount, ")");
  end
  scrollItems_Card[itemIndex].text_Name.text = name;

  local lvUpNeed = CollectCard.GetLevelUpNeed(showDatas[currentIndex].id);
  if lvUpNeed ~= 0 then
    local count = CollectCard.GetCount(id);
    scrollItems_Card[itemIndex].text_Count.text = string.Concat(count, "/", lvUpNeed);
    if count >= lvUpNeed then
      LuaHelper.SetColor(scrollItems_Card[itemIndex].cardSmallController.image_Pic, Color.White);
    else
      LuaHelper.SetColor(scrollItems_Card[itemIndex].cardSmallController.image_Pic, Color.Gray);
    end
  else
    LuaHelper.SetColor(scrollItems_Card[itemIndex].cardSmallController.image_Pic, Color.White);
    scrollItems_Card[itemIndex].text_Count.text = string.Get(21453);
  end

  return true;
end

function UICollectBook.UpdateCardInfo(data)  
  local maxCount = CollectCard.GetMaxEquipCard();
  local vipCount = UIVIP.GetVIPLevel(EVIPPermission.Card);
  local vipString = string.GetColorText(string.Concat("(", vipCount, ")"), Color.Red);
  text_Combine.text = string.Concat(string.Get(21421), CollectCard.GetEquipedCount(), "/", maxCount + vipCount, vipString);

  if data == nil then return end    
  local cardLv = CollectCard.GetLv(data.id);
  local addName = "";
  if Define.IsDebugMode() then 
    addName = string.Concat("(", data.id, ")") ;
  end
  text_CombinationName.text = string.Concat(string.Get(data.name), addName);
  text_CombinationLevel.text = cardLv;
  local isMaxLv = CollectCard.IsMaxLv(data.id);
  if isMaxLv then 
    text_LvFront.text = string.Concat(string.Get(21453), " ", string.Get(71154));    
  else    
    text_LvFront.text = string.Get(71154);    
  end

  text_CardStory.text = string.Get(data.story);

  local powerValue = CollectCard.GetPowerValue(data.id);
  for k, v in pairs(textGroup_CardValue)do
    if powerValue[k] ~= nil and powerValue[k].kind ~= 0 then 
      local str = string.Concat(this.GetAttributeString(powerValue[k].kind), "+", powerValue[k].value);      
      if cardLv > 0 and not isMaxLv then         
        local plusStr = string.GetColorText(string.Concat("(+", powerValue[k].plusValue, ")"), Color.LightGreen);
        str = string.Concat(str, plusStr);
      end
      v.text = str;
    else
      v.text = "";
    end
  end
  local count = 0;
  for k,v in pairs(data.card)do
    if v ~= 0 then 
      count = count + 1;
    end
  end
  
  scrollContent_Card:Reset(count);

  this.UpdateCardButton(data);
end

function UICollectBook.UpdateCardButton(data)
  if data == nil then return end   
  if CollectCard.GetLv(data.id) > 0 then 
    if CollectCard.IsEquiped(data.id) then --裝備中
      button_CardEquip.text.text = string.Get(98003);
      button_CardEquip.event.enabled = true;
      button_CardEquip.event:SetColor(Color.White);
    else
      button_CardEquip.text.text = string.Get(20165);
      if CollectCard.CanEquipCard() then 
        button_CardEquip.event.enabled = true;
        button_CardEquip.event:SetColor(Color.White);
      else
        button_CardEquip.event.enabled = false;
        button_CardEquip.event:SetColor(Color.Gray);        
      end
    end

    button_CardUpgrate.text.text = string.Get(40426)    
  else
    button_CardEquip.text.text = string.Get(20165);
    button_CardEquip.event.enabled = false;
    button_CardEquip.event:SetColor(Color.Gray);    
    
    button_CardUpgrate.text.text = string.Get(21422);
  end

  if CollectCard.CheckCanLevelUp(data.id) then 
    button_CardUpgrate.event.enabled = true;
    button_CardUpgrate.event:SetColor(Color.White);
  else
    button_CardUpgrate.event.enabled = false;
    button_CardUpgrate.event:SetColor(Color.Gray);    
  end
end

function UICollectBook.OnClick_Toogle(event)
  bool_Own = not bool_Own;
  image_Toogle.sprite = UI.GetCheckBoxSprite(bool_Own);
  this.SetShowDatas();
end

function UICollectBook.OnBuyCombnationSuccess()
  ShowCenterMessage(string.Get(21513));
  this.UpdateInfo();
end

function UICollectBook.OnClick_AddCombine(event)
  UISell.Launch(32, this.OnBuyCombnationSuccess);
end

function UICollectBook.OnClick_CardEquip(event)
  local data = showDatas[currentIndex];  
  CollectCard.EquipCard(data.id);
end

function UICollectBook.OnClick_CardUpgrate(event)
  local data = showDatas[currentIndex];  
  CollectCard.SendUpgrade(data.id);        
end

--Card View 
function UICollectBook.UpdateCardView()
  local pageCount = math.max(math.ceil(table.Count(showDatas) / MAX_VIEWCARD) , 1);
  text_CardPage.text = string.Concat(currentPage, "/",  pageCount);
  event_PageLeft.gameObject:SetActive(currentPage > 1);
  event_PageRight.gameObject:SetActive(currentPage < pageCount);
  for i = 1, MAX_VIEWCARD do
    local index = (currentPage - 1) * MAX_VIEWCARD + i;
    if showDatas[index] == nil then 
      rawImage_ViewCards[i].controller:SetShow(false);
    else
      rawImage_ViewCards[i].controller:SetShow(true);      
      rawImage_ViewCards[i].controller:SetData(showDatas[index].id, this.OnClick_ViewCard);      
      local flagId = showDatas[index].getFlag;
      if BitFlag.Get(flagId) then --if CollectCard.GetCount(showDatas[index].id) > 0 then       
        LuaHelper.SetColor(rawImage_ViewCards[i].controller.image_Pic, Color.White);
        rawImage_ViewCards[i].controller:SetRedDot(CollectCard.CheckSortRedDot(flagId));
      else
        LuaHelper.SetColor(rawImage_ViewCards[i].controller.image_Pic, Color.Gray);
        rawImage_ViewCards[i].controller:SetRedDot(false);
      end
    end    
  end
end

function UICollectBook.OnClick_CardLeftPage(event)
  if currentPage == 1 then 
    return;
  end

  currentPage = currentPage - 1;  
  this.UpdateCardView();
end

function UICollectBook.OnClick_CardRightPage(event)
  if table.Count(showDatas) <= currentPage * MAX_VIEWCARD then 
    return;
  end
  currentPage = currentPage + 1;
  this.UpdateCardView();
end

function UICollectBook.OnClick_ViewCard(event)
  local id = event.parameter;
  if BitFlag.Get(collectCardSortDatas[id].getFlag) then --CollectCard.GetCount(id) > 0 then 
    this.OpenCardDetail(id);
    if CollectCard.DeleteSortRedDot(collectCardSortDatas[id].getFlag) then 
      this.CheckCardSortRedDot();
      this.UpdateCardView();
    end
  else
    ShowCenterMessage(string.Get(21511));
  end
end

function UICollectBook.SortViewCard(a, b)
  local aCount = CollectCard.GetCount(a.id);
  local bCount = CollectCard.GetCount(b.id);
  if aCount == 0 and bCount == 0 then 
    return a.id < b.id;
  elseif aCount == 0 then 
    return false;
  elseif bCount == 0 then 
    return true;
  else
    return a.id < b.id;
  end
end

function UICollectBook.SortWarriorData(a, b)
  local aIsRedDot = false;
  for k, v in pairs(a.ids) do
    if collectWarriorDatas[v] ~= nil then 
      if BitFlag.Get(collectWarriorDatas[v].getFlag) and not BitFlag.Get(collectWarriorDatas[v].flag) then 
        aIsRedDot = true;
        break;
      end
    end
  end

  local bIsRedDot = false;
  for k, v in pairs(b.ids) do
    if collectWarriorDatas[v] ~= nil then 
      if BitFlag.Get(collectWarriorDatas[v].getFlag) and not BitFlag.Get(collectWarriorDatas[v].flag) then 
        bIsRedDot = true;
        break;
      end
    end
  end

  if aIsRedDot == bIsRedDot then 
    return a.index < b.index;
  elseif aIsRedDot then 
    return true;
  else
    return false;
  end
end

function UICollectBook.OnClick_CardMask(evnet)
  gameObject_CardDetail:SetActive(false);
end

function UICollectBook.OpenCardDetail(id)
  local cardData = collectCardSortDatas[id];
  if cardData == nil then 
    return 
  end
  gameObject_CardDetail:SetActive(true);
  cardController:SetData(id, CollectCard.GetCount(id));
end

--紅點判斷
function UICollectBook.CheckAllRedDot()
  this.CheckWarriorRedDot();
  this.CheckStyleRedDot();
  this.CheckCardRedDot();
  this.CheckCardSortRedDot();
end

function UICollectBook.CheckWarriorRedDot()
  for k, v in pairs(collectWarriorSortDatas)do
    for i = 1, 3 do             
      if v.ids[i] ~= 0 then 
        local warriorData = collectWarriorDatas[v.ids[i]];
        if warriorData ~= nil then
          local isOwn = BitFlag.Get(warriorData.getFlag);      
          local isGetReward = BitFlag.Get(warriorData.flag);
          if isOwn and not isGetReward then --擁有且沒領過獎                    
            RedDot.Add(ERedDot.CollectWarrior);    
            if UI.IsVisible(UICollectBook) then 
              this.UpdateRedDot();
            end        
            return;                
          end
        end        
      end
    end
  end
  RedDot.Remove(ERedDot.CollectWarrior);
  if UI.IsVisible(UICollectBook) then 
    this.UpdateRedDot();
  end
end

function UICollectBook.CheckStyleRedDot()
  local bag = Item.GetBag(EThings.Bag);
  for k, v in pairs(collectStyleDatas)do
    for k2, v2 in pairs(v.itemId)do
      if not CollectStyle.CheckEquiped(v.id, k2) then
        if bag ~= nil then
          for bagIndex, bagInfos in pairs(bag) do
            if bagInfos ~= nil and bagInfos.Id == v2 then
              RedDot.Add(ERedDot.CollectStyle);
              if UI.IsVisible(UICollectBook) then
                this.UpdateRedDot();
              end
              return;
            end
          end
        end
      end
    end
  end
  RedDot.Remove(ERedDot.CollectStyle);
  if UI.IsVisible(UICollectBook) then 
    this.UpdateRedDot();
  end        
end

function UICollectBook.CheckCardRedDot()
  for k,v in pairs(collectCardDatas)do 
    if CollectCard.CheckCanLevelUp(v.id) then    
      RedDot.Add(ERedDot.CollectCard);
      if UI.IsVisible(UICollectBook) then 
        this.UpdateRedDot();
      end        
      return;
    end 
  end
  RedDot.Remove(ERedDot.CollectCard);
  if UI.IsVisible(UICollectBook) then 
    this.UpdateRedDot();
  end        
end

function UICollectBook.CheckCardSortRedDot()
  if CollectCard.GetSortRedDotCount() > 0 then 
    RedDot.Add(ERedDot.CollectCardSort);
  else
    RedDot.Remove(ERedDot.CollectCardSort);
  end

  if UI.IsVisible(UICollectBook) then 
    this.UpdateRedDot();
  end 
end

function UICollectBook.SetRedDot(tag, active)
  tagObjects[tag].redDot:SetActive(active);
end

function UICollectBook.UpdateRedDot()
  this.SetRedDot(EUICollectTag.Warrior, RedDot.Check(ERedDot.CollectWarrior));
  this.SetRedDot(EUICollectTag.Style, RedDot.Check(ERedDot.CollectStyle));
  this.SetRedDot(EUICollectTag.Card, RedDot.Check(ERedDot.CollectCard));
  this.SetRedDot(EUICollectTag.CardView, RedDot.Check(ERedDot.CollectCardSort));
end