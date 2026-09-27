EUniqueBloodTag = {
  Equip = 1,
  Strength = 2,
  Upgrade = 3,
  Decompose = 4,
}

UIUniqueBlood = {}
local this = UIUniqueBlood;

this.name = "UIUniqueBlood";
this.uiController = nil;

local transform_FrameRect;

local gameObject_SubButtons;
local text_Piece;
local text_Submit;

--裝備
local gameObject_role;
local gameObject_equip;
local gameObject_Detail;
local gameObject_DetailRoot;
local uiCareer;
local jmpDrawer_Role;
local text_Name;
local text_Lv;
local text_NoNPC;
local text_NoStyle;
local scrollContent_Message;
local scrollItems_Message = {};

--強化
local gameObject_strength;
local gameObject_strengthCube;
local gameObject_att;
local text_ItemName;
local text_Title;
local text_PreLv;
local text_NewLv;
local text_StrengthPiece;
local text_MBefore;
local text_MAfter;
local text_SBefore;
local text_SAfter;

--升星
local gameObject_upgrade;
local rawImage_Mixed;
local image_questionMark;
local text_LevelLimit;

--分解
local gameObject_decompose;
local gameObject_Result;
local scrollContent_decompose;
local scrollItems_decompose = {};
local text_PieceCount;
local text_Count;
local text_DeResult;
local text_DeTitle;

--一鍵選擇
local gameObject_Setting;
local scrollContent_Check;
local scrollItems_Check = {};
local image_SelectAll;

--var
local currentTag = EUniqueBloodTag.Equip;
local currentRole = nil;
local followIndex = 1;
local currentKind = EUniqueBloodKind.All;
local isPlayingAnime = false;
local hasResult = false;
local currentItem = nil;
local strengthItem = nil;
local deMaxCount = 20;
local deTotalPiece = 0;
local nowEquip = nil;
local showDetail = false;

local tagData = {};
local equipCube = {}
local npcList = {};
local buttons = {};
local compoundCube = {};
local compoundItems = {};
local decomposeItems = {};

function UIUniqueBlood.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  local tempEvent;

    --region role+equip
  gameObject_role = uiController:FindGameObject("Role");
  gameObject_equip = uiController:FindGameObject("Equip");
  text_NoStyle = uiController:FindGameObject("Text_NoStyle");
  gameObject_Detail = uiController:FindGameObject("Image_DetailBG");
  gameObject_DetailRoot = UIStatusDetail.New(uiController:FindGameObject("Image_DetailBG"));
  uiCareer = UICareerController.New(uiController:FindGameObject("GameObject_Career"));

  jmpDrawer_Role = uiController:FindRawImage("RawImage_Role").gameObject:AddComponent(typeof(JmpDrawer));

  text_Name = uiController:FindText("Text_T6_Name");
  text_Lv = uiController:FindText("Text_T14_LvValue");
  text_NoNPC = uiController:FindText("Text_NoNPC");

  local temp = uiController:FindGameObject("EquipRoot").transform;

  for i = 1, 6 do
    equipCube[i] = UIItemController.New(temp:Find(string.Concat("Image_EquipBG (", i, ")")));
  end

  scrollContent_Message = uiController:FindScrollContent("ScrollContent_Message");
  scrollContent_Message.onInitialize = this.OnInitialize_ScrollContent_Message;
  scrollContent_Message.onItemChange = this.OnItemChange_ScrollContent_Message;
  scrollContent_Message:Initialize(1);

  tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = -1;

  tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = 1;

  tempEvent = uiController:FindEvent("Image_Detail");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_OpenDetail);

  tempEvent = uiController:FindEvent("Image_TakeOffAll");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_TakeOffAll);
  --endregion

    --region buttons
  gameObject_SubButtons = uiController:FindGameObject("SubButtons");

  text_Piece = uiController:FindText("Text_Piece");
  text_Submit = uiController:FindText("Text_Submit");

  for i = 1, 7 do
    buttons[i] = {};
  end

  buttons[1].image = uiController:FindImage("Image_All");
  buttons[2].image = uiController:FindImage("Image_Sky");
  buttons[3].image = uiController:FindImage("Image_Land");
  buttons[4].image = uiController:FindImage("Image_Human");
  buttons[5].image = uiController:FindImage("Image_Sun");
  buttons[6].image = uiController:FindImage("Image_Moon");
  buttons[7].image = uiController:FindImage("Image_Star");

  buttons[1].default = "but061D";
  buttons[2].default = "but303D";
  buttons[3].default = "but302D";
  buttons[4].default = "but301D";
  buttons[5].default = "but300D";
  buttons[6].default = "but299D";
  buttons[7].default = "but298D";

  buttons[1].click = "but061L";
  buttons[2].click = "but303L";
  buttons[3].click = "but302L";
  buttons[4].click = "but301L";
  buttons[5].click = "but300L";
  buttons[6].click = "but299L";
  buttons[7].click = "but298L";

  for i = 1, 7 do
    tempEvent = buttons[i].image:GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeCategory);
    tempEvent.parameter = i;
  end

  tempEvent = uiController:FindEvent("Image_Submit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Submit);

  tempEvent = uiController:FindEvent("Image_Cancel");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Cancel);

  --endregion

    --region strength
  gameObject_strength = uiController:FindGameObject("Strength");
  gameObject_att = uiController:FindGameObject("Att");
  gameObject_strengthCube = UIItemController.New(uiController:FindGameObject("Image_StrengthBG").transform);

  text_ItemName = uiController:FindText("Text_Name");
  text_Title = uiController:FindText("Text_Title");
  text_PreLv = uiController:FindText("Text_PreLv");
  text_NewLv = uiController:FindText("Text_NewLv");
  text_StrengthPiece = uiController:FindText("Text_StrengthPiece");
  text_MBefore = uiController:FindText("Text_MBefore");
  text_MAfter = uiController:FindText("Text_MAfter");
  text_SBefore = uiController:FindText("Text_SBefore");
  text_SAfter = uiController:FindText("Text_SAfter");

  --endregion

    --region upgrade
  gameObject_upgrade = uiController:FindGameObject("Upgrade");

  text_LevelLimit = uiController:FindText("Text_LevelLimit");

  for i = 1, 3 do
    compoundCube[i] = UIItemController.New(uiController:FindGameObject(string.Concat("Image_CompoundBG (",i,")")).transform);
  end

  image_questionMark = uiController:FindImage("Image_QuestionMark");
  rawImage_Mixed = uiController:FindRawImage("RawImage_Mixed");
  rawImage_Mixed.enabled = false;
  --endregion

    --region decompose
  gameObject_decompose = uiController:FindGameObject("Decompose");
  gameObject_Result = uiController:FindGameObject("Result");

  image_SelectAll = uiController:FindImage("Image_SelectAll");

  scrollContent_decompose = uiController:FindScrollContent("ScrollContent_DeItem");
  scrollContent_decompose.onInitialize = this.OnInitialize_ScrollContent_Decompose;
  scrollContent_decompose.onItemChange = this.OnItemChange_ScrollContent_Decompose;
  scrollContent_decompose:Initialize(1);

  text_PieceCount = uiController:FindText("Text_PieceCount");
  text_Count = uiController:FindText("Text_Count");
  text_DeResult = uiController:FindText("Text_DeResult");
  text_DeTitle = uiController:FindText("Text_DeTitle");

  tempEvent = uiController:FindEvent("Image_Setting");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Setting);

  tempEvent = uiController:FindEvent("Image_Decompose");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Decompose);

  tempEvent = uiController:FindEvent("Image_SelectAll");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectAll);

  tempEvent = uiController:FindEvent("Image_All");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_QuickSelect);
  --endregion

    --region Setting
  gameObject_Setting = uiController:FindGameObject("Setting");

  scrollContent_Check = uiController:FindScrollContent("ScrollContent_Equip");
  scrollContent_Check.onInitialize = this.OnInitialize_ScrollContent_Check;
  scrollContent_Check.onItemChange = this.OnItemChange_ScrollContent_Check;
  scrollContent_Check:Initialize(1);

  tempEvent = uiController:FindEvent("Image_Close");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SettingClose);

  tempEvent = uiController:FindEvent("Image_CloseResult");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ResultClose);
  --endregion
end

function UIUniqueBlood.OnOpen()
  currentTag = EUniqueBloodTag.Equip;
  currentKind = EUniqueBloodKind.All;
  hasResult = false;
  showDetail = false;

  for i = 1, 7 do
    buttons[i].image.sprite = UI.GetSprite(buttons[i].default);
  end

  this.GetNpcList();
  gameObject_Result:SetActive(false);
  table.Clear(tagData);

  return true;
end

function UIUniqueBlood.OnVisible(visible)
  if visible then
    tagData[EUniqueBloodTag.Equip] = { string.Get(20165), nil };

    if not Network.InUnbound then
      tagData[EUniqueBloodTag.Strength] = { string.Get(20029), nil };
      tagData[EUniqueBloodTag.Upgrade] = { string.Get(10601), nil };
      tagData[EUniqueBloodTag.Decompose] = { string.Get(21026), nil };
    end

    UIFrame.Active(transform_FrameRect, string.Get(24000), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagData, currentTag);

    this.UpdateUI();
  end
end

function UIUniqueBlood.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 76, this);
  end

  return true;
end

function UIUniqueBlood.OnClick_Tag(tag)
  currentTag = tag;
  currentKind = EUniqueBloodKind.All;
  this.ResetValue();
  this.SetResultPage(false);

  if UI.IsVisible(UIBag) then
    UI.Close(UIBag)
  end

  this.UpdateUI();

  return true;
end

function UIUniqueBlood.OnClick_Close()
  jmpDrawer_Role:Disable();

  this.ResetValue();
  table.Clear(npcList);

  UI.Close(UIUniqueBlood);

  if UI.IsVisible(UIBag) then
    UI.Close(UIBag);
  end

  return true;
end

  --region update
function UIUniqueBlood.UpdateUI(reset)
  gameObject_role:SetActive(currentTag == EUniqueBloodTag.Equip);
  gameObject_equip:SetActive(currentTag == EUniqueBloodTag.Equip);
  gameObject_strength:SetActive(currentTag == EUniqueBloodTag.Strength);
  gameObject_upgrade:SetActive(currentTag == EUniqueBloodTag.Upgrade);
  gameObject_decompose:SetActive(currentTag == EUniqueBloodTag.Decompose);
  gameObject_SubButtons:SetActive(currentTag == EUniqueBloodTag.Strength or currentTag == EUniqueBloodTag.Upgrade)
  gameObject_Detail:SetActive(showDetail);
  gameObject_Setting:SetActive(false);

  text_Piece.text = RoleCount.Get(ERoleCount.UniqueBloodPiece);

  this.UpdateRole();
  this.UpdateEquip(reset);
  this.UpdateDetail();
  this.UpdateStrength();
  this.UpdateUpgrade();
  this.UpdateDecompose(reset);
  this.UpdateOpenBag();
  UIBag.UpdateUI();
end

function UIUniqueBlood.UpdateOpenBag()
  for i = 1, 7 do
    buttons[i].image.sprite = UI.GetSprite(buttons[i].default);
  end

  buttons[currentKind + 1].image.sprite = UI.GetSprite(buttons[currentKind + 1].click);

  --UIItemInfo func順序是反過來的
  UI.Open(UIBag,
    function(bagIndex, itemId)
      currentItem = Item.GetBagItem(EThings.UniqueBlood, bagIndex);
      currentItem.bagIndex = bagIndex;
      if currentTag == EUniqueBloodTag.Equip then
        UI.Open(UIItemInfo, EThings.UniqueBlood, bagIndex, 0,
          string.Get(10452),
          function()
            if currentItem == nil then return end
            if FightField.isInBattle then return end
            if currentRole == nil then
              ShowCenterMessage(string.Get(24040));
              return;
            end

            local count = table.Count(UniqueBlood.nowEquipItems);
            nowEquip = nil;

            for i = 1, count do
              local data = UniqueBlood.nowEquipItems[i];
              if data ~= nil then
                if data:GetKind() == currentItem:GetKind() then
                  nowEquip = data;
                  break;
                end
              end
            end

            if nowEquip == nil then
              this.SendEquip(1, currentItem);
            else
              this.SendEquip(3, nowEquip, currentItem);
            end
            UI.Close(UIItemInfo);
            this.UpdateUI(true);
          end
        );
      elseif currentTag == EUniqueBloodTag.Strength then
        UI.Open(UIItemInfo, EThings.UniqueBlood, bagIndex, 0,
          string.Get(20029),
          function()
            if currentItem == nil then return end
            if FightField.isInBattle then return end

            if strengthItem ~= nil then
              Item.ClearAllInUse();
            end

            strengthItem = currentItem;
            Item.SetItemInUse(bagIndex);
            UI.Close(UIItemInfo);
            this.UpdateUI();
        end
        );
      elseif currentTag == EUniqueBloodTag.Upgrade then
        UI.Open(UIItemInfo, EThings.UniqueBlood, bagIndex, 0,
          this.SetUpgradeText(),
          function()
            if currentItem == nil then return end
            if FightField.isInBattle then return end

            if compoundItems[1] == nil then
              compoundItems[1] = currentItem;
              Item.SetItemInUse(bagIndex);
            else
              if compoundItems[2] ~= nil then
                Item.ClearAllInUse();
                local bag = Item.GetBag(EThings.UniqueBlood);

                for k, v in pairs(bag) do
                  if v.sn == compoundItems[1].sn then
                    Item.SetItemInUse(k);
                    break;
                  end
                end
              end

              compoundItems[2] = currentItem;
              Item.SetItemInUse(bagIndex);
            end

            UI.Close(UIItemInfo);
            this.UpdateUI();
        end
        );
      elseif currentTag == EUniqueBloodTag.Decompose then
        if table.Count(decomposeItems) < deMaxCount then
          table.insert(decomposeItems, currentItem);
          Item.DelBagItem(EThings.UniqueBlood, bagIndex);
          deTotalPiece = deTotalPiece + UniqueBlood.GetDePiece(currentItem.Id);
        else
          ShowCenterMessage(string.Get(24038));
        end

        this.UpdateUI();
      end
    end,
    Vector2.New(260, -25),
    false,
    nil,
    function(itemSave)
      if itemDatas[itemSave.Id] == nil then return false end
      if itemDatas[itemSave.Id].kind ~= EItemKind.UniqueBlood then return false end
      if currentKind ~= EUniqueBloodKind.All then
        if itemSave:GetKind() ~= currentKind then return false end
      end

      if currentTag == EUniqueBloodTag.Upgrade then
        if itemSave.level >= itemSave.maxLevel then return false end

        if compoundItems[1] ~= nil then
          if itemSave.isLock then return false end
          if compoundItems[1].Id ~= itemSave.Id then return false end
        else
          if itemSave.level ~= itemSave.nowMaxLv then return false end
        end
      elseif currentTag == EUniqueBloodTag.Strength then
        if itemSave.level == itemSave.nowMaxLv then return false end
      elseif currentTag == EUniqueBloodTag.Decompose then
        if itemSave.isLock then return false end
      end

      return true;
      end,
    false,
    false,
    EThings.UniqueBlood
  );
end

function UIUniqueBlood.UpdateRole()
  if currentTag ~= EUniqueBloodTag.Equip then return end

  if currentRole == nil and table.Count(npcList.role) ~= 0 then
    currentRole = npcList.role[1];
    followIndex = 1;
  end

  uiCareer.gameObject:SetActive(currentRole ~= nil);

  if currentRole ~= nil then
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

    text_Name.text = currentRole.name;
    text_Lv.text = currentRole:GetAttribute(EAttribute.Lv);
  else
    jmpDrawer_Role:Disable();
    text_Name.text = "";
    text_Lv.text = "";
  end
end

function UIUniqueBlood.UpdateEquip(reset)
  if currentTag ~= EUniqueBloodTag.Equip then return end

  text_NoNPC.enabled = currentRole == nil;

  if currentRole ~= nil then
    UniqueBlood.GetEquipItem(currentRole.npcId);
    UniqueBlood.SetAllSuitCountText();

    for i = 1, 6 do
      equipCube[i]:SetItem(0);
      equipCube[i]:SetEvent();
    end

    local count = table.Count(UniqueBlood.nowEquipItems);

    for i = 1, count do
      if UniqueBlood.nowEquipItems[i] ~= nil then
        equipCube[UniqueBlood.nowEquipItems[i]:GetKind()]:SetItem(UniqueBlood.nowEquipItems[i].Id, 1, UniqueBlood.nowEquipItems[i]);
        equipCube[UniqueBlood.nowEquipItems[i]:GetKind()]:SetEvent(function()
          UI.Open(UIItemInfo, EThings.UniqueBloodEquip, UniqueBlood.nowEquipItems[i].bagIndex, 0,
            string.Get(98003),
            function()
              if currentRole == nil then return end
              if FightField.isInBattle then return end

              this.SendEquip(2, UniqueBlood.nowEquipItems[i]);
            end
          );
        end);
      else
        equipCube[i]:SetItem(0);
        equipCube[i]:SetEvent();
      end
    end
  else
    for i = 1, 6 do
      equipCube[i]:SetItem(0);
      equipCube[i]:SetEvent();
    end
  end

  text_NoStyle:SetActive(table.Count(UniqueBlood.suitCount) == 0);

  if reset then
    scrollContent_Message:Reset(table.Count(UniqueBlood.suitCount));
  else
    scrollContent_Message:Refresh(table.Count(UniqueBlood.suitCount));
  end
end

function UIUniqueBlood.UpdateStrength()
  if currentTag ~= EUniqueBloodTag.Strength then return end

  text_Submit.text = string.Get(20029);

  if strengthItem ~= nil then
    local lv1 = strengthItem.level;
    local lv2 = lv1 + 1;
    local suit = UniqueBlood.GetSuitData(strengthItem:GetStyleID());

    gameObject_att:SetActive(true);

    text_ItemName.text = itemDatas[strengthItem.Id].name;
    text_Title.text = string.Get(suit.stringId);
    text_PreLv.text = lv1;

    if lv1 == strengthItem.nowMaxLv then
      text_PreLv.text = string.Get(20170);
    else
      text_PreLv.text = lv1;
    end

    if lv2 >= strengthItem.nowMaxLv then
      lv2 = strengthItem.nowMaxLv;
      text_NewLv.text = string.Get(20170);
    else
      text_NewLv.text = lv2;
    end

    if lv1 == strengthItem.nowMaxLv then
      text_StrengthPiece.text = 0;
    else
      text_StrengthPiece.text = UniqueBlood.GetLevelUpPiece(strengthItem:GetUpgradeGroup(), lv2);
    end

    text_MBefore.text = string.Concat(UniqueBlood.GetAttrText(strengthItem:GetMainAtt(), true), UniqueBlood.GetMainAttValue(strengthItem, lv1));
    text_MAfter.text = string.Concat(UniqueBlood.GetAttrText(strengthItem:GetMainAtt(), true), UniqueBlood.GetMainAttValue(strengthItem, lv2));
    text_SBefore.text = string.Concat(UniqueBlood.GetAttrText(strengthItem:GetSubAtt(), true), UniqueBlood.GetSubAttValue(strengthItem, lv1));

    local newValue = UniqueBlood.GetSubAttValue(strengthItem, lv2);

    if newValue == 0 then
      text_SAfter.text = "?";
    else
      text_SAfter.text = string.Concat(UniqueBlood.GetAttrText(strengthItem:GetSubAtt(), true), newValue);
    end

    gameObject_strengthCube:SetItem(strengthItem.Id, 1, strengthItem);
  else
    gameObject_att:SetActive(false);

    text_Title.text = "";
    text_ItemName.text = "";
    text_PreLv.text = "?";
    text_NewLv.text = "?";
    text_StrengthPiece.text = 0;

    gameObject_strengthCube:SetItem(0);
  end
end

function UIUniqueBlood.UpdateUpgrade()
  if currentTag ~= EUniqueBloodTag.Upgrade then return end

  text_Submit.text = string.Get(10601);

  if compoundItems[1] ~= nil then
    text_LevelLimit.text = string.format(string.Get(24033), compoundItems[1].starLv, compoundItems[1].starLv + 1);
    hasResult = false;
    compoundItems[3] = nil;
  else
    text_LevelLimit.text = "";
  end

  for i = 1, 3 do
    if compoundItems[i] ~= nil then
      compoundCube[i]:SetItem(compoundItems[i].Id, 1, compoundItems[i]);
      compoundCube[i]:SetEvent(function()
        UI.Open(UIItemInfo, EThings.UniqueBlood, compoundItems[i].bagIndex, 0,
          string.Get(20110),
          function()
            if currentItem == nil then return end
            if FightField.isInBattle then return end

            if i == 1 then
              table.Clear(compoundItems);
              Item.ClearAllInUse();
            elseif i == 2 then
              compoundItems[i] = nil;
              Item.ClearAllInUse();
              Item.SetItemInUse(compoundItems[1].bagIndex);
             elseif i == 3 then
              compoundItems[i] = nil;
              Item.ClearAllInUse();
              hasResult = false;
            end

            UI.Close(UIItemInfo);
            this.UpdateUI();
          end
        );
      end
      );
    else
      compoundCube[i]:SetItem(0);
      compoundCube[i]:SetEvent();
    end
  end

  image_questionMark.enabled = not hasResult;
end

function UIUniqueBlood.UpdateDecompose(reset)
  if currentTag ~= EUniqueBloodTag.Decompose then return end

  text_PieceCount.text = deTotalPiece;
  text_Count.text = string.Concat(table.Count(decomposeItems), "/", deMaxCount);

  if reset then
    scrollContent_decompose:Reset(deMaxCount);
  else
    scrollContent_decompose:Refresh(deMaxCount);
  end
end

function UIUniqueBlood.UpdateSetting()
  scrollContent_Check:Refresh(table.Count(EUniqueBloodKind) - 1);

  image_SelectAll.sprite = UI.GetCheckBoxSprite(UniqueBlood.IsSelectAll());
end

function UIUniqueBlood.UpdateResult()
  text_DeTitle.text = string.format(string.Get(24030), table.Count(decomposeItems), deTotalPiece);
  text_DeResult.text = string.format(string.Get(24031), deTotalPiece);

  ShowCenterMessage(string.format(string.Get(24025), deTotalPiece));
end

function UIUniqueBlood.UpdateDetail()
  gameObject_Detail:SetActive(showDetail);

  if currentRole ~= nil then
    gameObject_DetailRoot:ShowInfo(currentRole);
  end
end
  --endregion

  --region scrollItems
function UIUniqueBlood.OnInitialize_ScrollContent_Message(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Message[itemIndex] = {};
    scrollItems_Message[itemIndex].text_Title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");
    scrollItems_Message[itemIndex].text_Attr = scrollItems[itemIndex]:Find("Text_Attr"):GetComponent("Text");
  end
end

function UIUniqueBlood.OnItemChange_ScrollContent_Message(dataIndex, itemIndex)
  local index = dataIndex + 1;

  if UniqueBlood.suitCount[index] ~= nil then
    local data = UniqueBlood.suitCount[index];

    scrollItems_Message[itemIndex].text_Title.text = string.Get(data.stringId);
    scrollItems_Message[itemIndex].text_Attr.text = data.text;
  else
    scrollItems_Message[itemIndex].text_Title.text = "";
    scrollItems_Message[itemIndex].text_Attr.text = "";
  end
  return true;
end

function UIUniqueBlood.OnInitialize_ScrollContent_Decompose(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_decompose[itemIndex] = {};
    scrollItems_decompose[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_decompose[itemIndex].image_Remove = scrollItems[itemIndex]:Find("Image_Remove"):GetComponent("Image");
    scrollItems_decompose[itemIndex].event_Remove = scrollItems[itemIndex]:Find("Image_Remove"):GetComponent("UIEvent");
    scrollItems_decompose[itemIndex].event_Remove:SetListener(EventTriggerType.PointerClick, this.OnClick_Remove);
    scrollItems_decompose[itemIndex].item = UIItemController.New(scrollItems[itemIndex]:Find("RawImage_"));
  end
end

function UIUniqueBlood.OnItemChange_ScrollContent_Decompose(dataIndex, itemIndex)
  local index = dataIndex + 1;

  scrollItems_decompose[itemIndex].event_Remove.parameter = index;

  if decomposeItems[index] ~= nil then
    local data = itemDatas[decomposeItems[index].Id];

    scrollItems_decompose[itemIndex].text_Name.text = data.name;
    scrollItems_decompose[itemIndex].item:SetItem(decomposeItems[index].Id, 1, decomposeItems[index]);
    scrollItems_decompose[itemIndex].image_Remove.enabled = true;
  else
    scrollItems_decompose[itemIndex].text_Name.text = "";
    scrollItems_decompose[itemIndex].item:SetItem(0);
    scrollItems_decompose[itemIndex].image_Remove.enabled = false;
  end

  return true;
end

function UIUniqueBlood.OnInitialize_ScrollContent_Check(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Check[itemIndex] = {};
    scrollItems_Check[itemIndex].text = scrollItems[itemIndex]:Find("Text_Item"):GetComponent("Text");
    scrollItems_Check[itemIndex].quality = {};
    for i = 1, UniqueBlood.qualityCount do
      scrollItems_Check[itemIndex].quality[i] = {};
      scrollItems_Check[itemIndex].quality[i].sprite = scrollItems[itemIndex]:Find("Text_Item"):Find(string.Concat("Image_CheckBox (", i, ")")):GetComponent("Image");
      scrollItems_Check[itemIndex].quality[i].event = scrollItems[itemIndex]:Find("Text_Item"):Find(string.Concat("Image_CheckBox (", i, ")")):GetComponent("UIEvent");
      scrollItems_Check[itemIndex].quality[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_CheckBoxEquip);
    end
  end
end

function UIUniqueBlood.OnItemChange_ScrollContent_Check(dataIndex, itemIndex)
  local index = dataIndex + 1;

  if UniqueBlood.item[index] ~= nil then
    scrollItems_Check[itemIndex].text.text = UniqueBlood.TransKindToText(index);

    for i = 1, 5 do
      scrollItems_Check[itemIndex].quality[i].sprite.sprite = UI.GetCheckBoxSprite(UniqueBlood.item[index].check[i]);
      scrollItems_Check[itemIndex].quality[i].event.parameter = index * 10 + i;
    end
  end

  return true;
end
  --endregion

  --region function
function UIUniqueBlood.GetNpcList()
  table.Clear(npcList);

  npcList.role = {};
  npcList.oriFollowIndex = {};
  local role = nil;

  for i = 1, Role.maxFollowNpc do
    if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) then

      role = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);
      if role ~= nil and role.data.canGrow ~= 0 and role.data.canGrow ~= 2 then
        table.insert(npcList.role, role);
        table.insert(npcList.oriFollowIndex, i);
      end
    end
  end
end

function UIUniqueBlood.GetFollowIndex()
  for i = 1, table.Count(npcList.role) do
    if npcList.role[i].npcId == currentRole.npcId then
      return npcList.oriFollowIndex[i];
    end
  end

  return 0;
end

function UIUniqueBlood.GetEmptyIndex(bagkind)
  local bag = Item.GetBag(bagkind)
  local count = Item.GetBagCount(bagkind);

  for i = 1, count do
    if bag[i] == nil then
      return i;
    end
  end

  return count + 1;
end

function UIUniqueBlood.SetUpgradeText()
  if compoundItems[1] == nil then
    return string.Get(10601);
  else
    return string.Get(24034);
  end
end

function UIUniqueBlood.ResetValue()
  hasResult = false;
  strengthItem = nil;
  currentItem = nil;
  currentRole = nil;
  deTotalPiece = 0;
  showDetail = false;

  for i = 1, table.Count(decomposeItems) do
    Item.SetBagItem(EThings.UniqueBlood, this.GetEmptyIndex(EThings.UniqueBlood), decomposeItems[i]);
  end

  Item.ClearAllInUse();

  table.Clear(compoundItems)
  table.Clear(decomposeItems)
end

function UIUniqueBlood.ShowDecomposeMessage(sn)
  for k, v in pairs(decomposeItems) do
    if v.sn == sn then
      local message = string.format(string.Get(21160), itemDatas[v.Id]:GetName(true), 1);
      Chat.AddMessage(EChannel.System, message);
      break;
    end
  end
end

function UIUniqueBlood.SetResultPage(isOpen)
  if isOpen then
    gameObject_Result:SetActive(true);
    this.UpdateResult();
  else
    gameObject_Result:SetActive(false);
  end
end
  --endregion

  --region onclick
function UIUniqueBlood.OnClick_OpenDetail()
  if currentRole == nil then return end;
  showDetail = not showDetail;

  this.UpdateDetail();
end

function UIUniqueBlood.OnClick_TakeOffAll()
  local totalCount = table.Count(UniqueBlood.nowEquipItems);
  local count = 0;
  local hasOver = false;

  while Item.GetBagCount(EThings.UniqueBlood) + totalCount > Item.currentUniqueBloodGrid do
    totalCount = totalCount - 1;
    hasOver = true;
  end

  for _, v in pairs(UniqueBlood.nowEquipItems) do
    if count < totalCount then
      this.SendEquip(2, v);
    else
      break;
    end
    count = count + 1;
  end

  if hasOver then
    ShowCenterMessage(string.Get(24049));
  end
end

function UIUniqueBlood.OnClick_SwitchRole(uiEvent)
  currentRole = nil;

  if uiEvent.parameter > 0 then
    if followIndex == table.Count(npcList.role) then
      currentRole = npcList.role[1];
      followIndex = 1;
    else
      followIndex = followIndex + 1;
      currentRole = npcList.role[followIndex];
    end
  else
    if followIndex == 1 then
      followIndex = table.Count(npcList.role);
      currentRole = npcList.role[followIndex];
    else
      followIndex = followIndex - 1;
      currentRole = npcList.role[followIndex];
    end
  end

  this.UpdateUI();
end

function UIUniqueBlood.OnClick_ChangeCategory(uiEvent)
  if compoundItems[1] ~= nil then return end

  if currentKind ~= uiEvent.parameter - 1 then
    currentKind = uiEvent.parameter - 1;
  end

  this.UpdateUI();
end

function UIUniqueBlood.OnClick_Submit()
  if Network.InUnbound then
    ShowCenterMessage(string.Get(90156));
    return;
  end

  if currentTag == EUniqueBloodTag.Strength then
    if strengthItem == nil then return end
    local nextLv = strengthItem.level + 1;

    if nextLv <= strengthItem.nowMaxLv then
      if UniqueBlood.GetLevelUpPiece(strengthItem:GetUpgradeGroup(), nextLv) > RoleCount.Get(ERoleCount.UniqueBloodPiece) then
        ShowCenterMessage(string.Get(21180));
        return;
      end

      this.SendStrength(nextLv, strengthItem.sn);
    else
      ShowCenterMessage(string.Get(24022));
    end
  elseif currentTag == EUniqueBloodTag.Upgrade then
    if compoundItems[2] == nil then return end
    local hasOver = false;

    if compoundItems[2].starLv > 0 then
      hasOver = true;
      ShowCheckMessage(function(result)
        if result then
          this.SendUpgrade();
        end
      end,
        string.Get(24028)
      );
    end

    if not hasOver then
      if compoundItems[2].level > 1 then
        hasOver = true;
        ShowCheckMessage(function(result)
          if result then
            this.SendUpgrade();
          end
        end,
          string.Get(24029)
        );
      end
    end

    if not hasOver then
      this.SendUpgrade();
    end
  end
end

function UIUniqueBlood.OnClick_Cancel()
  table.Clear(compoundItems);
  strengthItem = nil;
  currentItem = false;
  Item.ClearAllInUse();
  hasResult = false;

  this.UpdateUI(true);
end

function UIUniqueBlood.OnClick_Setting()
  gameObject_Setting:SetActive(true);
  this.uiController.canvas.sortingOrder = this.uiController.canvas.sortingOrder + 10;

  this.UpdateSetting();
end

function UIUniqueBlood.OnClick_SettingClose()
  gameObject_Setting:SetActive(false);
  this.uiController.canvas.sortingOrder = this.uiController.canvas.sortingOrder - 10;
end

function UIUniqueBlood.OnClick_ResultClose()
  this.SetResultPage(false);
end

function UIUniqueBlood.OnClick_Remove(uiEvent)
  local index = uiEvent.parameter;

  Item.SetBagItem(EThings.UniqueBlood, this.GetEmptyIndex(EThings.UniqueBlood), decomposeItems[index]);
  deTotalPiece = deTotalPiece - UniqueBlood.GetDePiece(decomposeItems[index].Id);
  table.remove(decomposeItems, index);

  this.UpdateUI(true);
end

function UIUniqueBlood.OnClick_Decompose()
  if Network.InUnbound then
    ShowCenterMessage(string.Get(90156));
    return;
  end

  local hasBreak = false;
  if table.Count(decomposeItems) == 0 then return end

  for _, v in pairs(decomposeItems) do
    if v.starLv > 0 then
      hasBreak = true;
      ShowCheckMessage(function(result)
        if result then
          this.SendDecompose();
        end
      end,
        string.Get(24026)
      );
      break;
    end

    if not hasBreak then
      if v.level > 1 then
        hasBreak = true;
        ShowCheckMessage(function(result)
          if result then
            this.SendDecompose();
          end
        end,
          string.Get(24027)
        );
        break;
      end
    end
  end

  if not hasBreak then
    this.SendDecompose();
  end
end

function UIUniqueBlood.OnClick_CheckBoxEquip(uiEvent)
  local index = math.floor(uiEvent.parameter / 10);
  local checkIdx = math.fmod(uiEvent.parameter, 10);

  UniqueBlood.item[index].check[checkIdx] = not UniqueBlood.item[index].check[checkIdx];
  scrollItems_Check[index - 1].quality[checkIdx].sprite.sprite = UI.GetCheckBoxSprite(UniqueBlood.item[index].check[checkIdx]);

  UniqueBlood.item[index].check[checkIdx] = UniqueBlood.item[index].check[checkIdx];

  image_SelectAll.sprite = UI.GetCheckBoxSprite(UniqueBlood.IsSelectAll());
end

function UIUniqueBlood.OnClick_SelectAll()
  if not UniqueBlood.IsSelectAll() then
    for i = 1, table.Count(EUniqueBloodKind) - 1 do
      for j = 1, UniqueBlood.qualityCount do
        UniqueBlood.item[i].check[j] = true;
      end
    end
  else
    for i = 1, table.Count(EUniqueBloodKind) - 1 do
      for j = 1, UniqueBlood.qualityCount do
        UniqueBlood.item[i].check[j] = false;
      end
    end
  end

  this.UpdateSetting();
end

function UIUniqueBlood.OnClick_QuickSelect()
  local bag = Item.GetBag(EThings.UniqueBlood);
  local hasOther = false;

  if UniqueBlood.NoSelect() then
    ShowCenterMessage(string.Get(24041));
    return;
  end

  for k, v in pairs(bag) do
    if table.Count(decomposeItems) < deMaxCount then
      if not v.isLock then
        for j = 1, table.Count(EUniqueBloodKind) - 1 do
          if j == v:GetKind() then
            for i = 1, UniqueBlood.qualityCount do
              if UniqueBlood.item[j].check[i] and v.quality == i - 1 then
                table.insert(decomposeItems, v);
                deTotalPiece = deTotalPiece + UniqueBlood.GetDePiece(v.Id);
                Item.DelBagItem(EThings.UniqueBlood, k);
              end
            end
          end
        end
      end
    else
      hasOther = true;
      break;
    end
  end

  if hasOther then
    ShowCenterMessage(string.Get(24038));
  end

  this.UpdateUI(true);
end
  --endregion

  --region send
--C:121-001<裝備/卸下> Kind(1) + 武將ID(2) + 跟隨武將索引(1) + SN(8) + SN(8) [Kind=1.裝備 2.卸下 3.交換]
function UIUniqueBlood.SendEquip(kind, item1, item2)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  UniqueBlood.SaveAttribute(this.GetFollowIndex());

  sendBuffer:Clear();
  sendBuffer:WriteByte(kind);
  sendBuffer:WriteUInt16(currentRole.npcId);
  sendBuffer:WriteByte(this.GetFollowIndex());
  sendBuffer:WriteInt64(item1.sn);

  if kind == 3 then
    sendBuffer:WriteInt64(item2.sn);
  end

  Network.Send(121, 1, sendBuffer);
end

--C:121-004 <等級升級> 要升的等級(1) + SN(8)
function UIUniqueBlood.SendStrength(lv, sn)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  sendBuffer:Clear();
  sendBuffer:WriteByte(lv);
  sendBuffer:WriteInt64(sn);
  Network.Send(121, 4, sendBuffer);
end

--C:121-002 <分解> + count(1) + SN(8)
function UIUniqueBlood.SendDecompose()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  local count = table.Count(decomposeItems);

  sendBuffer:Clear();
  sendBuffer:WriteByte(count);

  for i = 1, count do
    sendBuffer:WriteInt64(decomposeItems[i].sn);
  end

  Network.Send(121, 2, sendBuffer);
end

--C:121-003 <升星> 主要SN(8) + 材料SN(8)
function UIUniqueBlood.SendUpgrade()
  sendBuffer:Clear();
  sendBuffer:WriteInt64(compoundItems[1].sn);
  sendBuffer:WriteInt64(compoundItems[2].sn);

  Network.Send(121, 3, sendBuffer);
end

  --endregion

  --region receive
function UIUniqueBlood.ReceiveEquip(npcId, sn)
  local bagIndex = 1;
  local bag = Item.GetBag(EThings.UniqueBlood);
  local item;

  for k, v in pairs(bag) do
    if v.sn == sn then
      item = v;
      bagIndex = k;
      break;
    end
  end

  if item ~= nil then
    item.equipId = npcId;
    Item.DelBagItem(EThings.UniqueBlood, bagIndex)
    Item.SetBagItem(EThings.UniqueBloodEquip, this.GetEmptyIndex(EThings.UniqueBloodEquip), item);

    UniqueBlood.ShowEquipMessage(item.Id, this.GetFollowIndex(), true);

    currentItem = nil;
    UI.Close(UIItemInfo);
    UI.Close(UIBag);
    this.UpdateUI(true);
  else
    logError("sn error");
  end
end

function UIUniqueBlood.ReceiveTakeOff(npcId, sn)
  local bag = Item.GetBag(EThings.UniqueBloodEquip);
  local index = 0;
  local item = nil;

  for k, v in pairs(bag) do
    if v.sn == sn and v.equipId == npcId then
      item = v;
      index = k;
      break;
    end
  end

  if item ~= nil then
    Item.DelBagItem(EThings.UniqueBloodEquip, index)
    item.equipId = 0;
    Item.SetBagItem(EThings.UniqueBlood, this.GetEmptyIndex(EThings.UniqueBlood), item);

    if nowEquip == nil then
      currentItem = nil;
      UI.Close(UIItemInfo);
      UI.Close(UIBag);
      if UI.IsVisible(UIUniqueBlood) then
        this.UpdateUI(true);
      end
    else
      nowEquip = nil;
      this.SendEquip(1, currentItem);
    end

    UniqueBlood.ShowEquipMessage(item.Id, this.GetFollowIndex(), false);
  else
    logError("sn error");
  end
end

function UIUniqueBlood.ReceiveExchange(npcId, sn, sn2)
  local bagIndex = 1;
  local bag = Item.GetBag(EThings.UniqueBlood);
  local item;

  for k, v in pairs(bag) do
    if v.sn == sn2 then
      item = v;
      bagIndex = k;
      break;
    end
  end

  item.equipId = npcId;
  Item.DelBagItem(EThings.UniqueBlood, bagIndex)
  Item.SetBagItem(EThings.UniqueBloodEquip, this.GetEmptyIndex(EThings.UniqueBloodEquip), item);

  UniqueBlood.ShowEquipMessage(item.Id, this.GetFollowIndex(), true);

  bag = Item.GetBag(EThings.UniqueBloodEquip);

  for k, v in pairs(bag) do
    if v.sn == sn and v.equipId == npcId then
      item = v;
      bagIndex = k;
      break;
    end
  end

  Item.DelBagItem(EThings.UniqueBloodEquip, bagIndex)
  item.equipId = 0;
  Item.SetBagItem(EThings.UniqueBlood, this.GetEmptyIndex(EThings.UniqueBlood), item);

  currentItem = nil;
  nowEquip = nil;
  UI.Close(UIItemInfo);
  UI.Close(UIBag);
  this.UpdateUI(true);
end

function UIUniqueBlood.ReceiveDecompose()
  this.SetResultPage(true);
  deTotalPiece = 0;
  table.Clear(decomposeItems);
  this.UpdateUI();
end

function UIUniqueBlood.ReceiveUpgrade(sn, star)
  if sn == compoundItems[1].sn then
    local bag = Item.GetBag(EThings.UniqueBlood);

    for k, v in pairs(bag) do
      if v.sn == sn then
        v.starLv = star;
        v:RefreshData();
        compoundItems[3] = v;

        ShowCenterMessage(string.Get(24054));
        break;
      end
    end

    compoundItems[1] = nil;
    compoundItems[2] = nil;
    image_questionMark.enabled = false;
    hasResult = true;
    this.UpdateUI();
  end
end

function UIUniqueBlood.ReceiveStrength(sn, lv)
  local bag = Item.GetBag(EThings.UniqueBlood);
  local msg = string.format(string.Get(24102), UniqueBlood.GetLevelUpPiece(strengthItem:GetUpgradeGroup(), lv));

  ShowCenterMessage(msg);
  Chat.AddMessage(EChannel.System, msg);

  for k, v in pairs(bag) do
    if v.sn == sn then
      v.level = lv;
      strengthItem = v;
      strengthItem.bagIndex = k;
      ShowCenterMessage(string.Get(24053));
      break;
    end
  end

  this.UpdateUI();
end

  --endregion
