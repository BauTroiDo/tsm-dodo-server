ESoulTag = {
  Compound = 1,
  Equip = 2,
  Intensify = 3,
  Breakthrough = 4,
  SkillLv = 5,
  Affination = 6
}

ESoulCategory = {
  All = -1,
  N = 0,
  R = 1,
  SR = 2,
  SSR = 3,
}

ESoulAction = {
  Equip = 1,
  Compound = 2,
  Intensify = 3,
  Breakthrough = 4,
  SkillLv = 5,
  Affination = 6,
  Takeoff = 7,
  MoveOut = 8,
  Upload = 9, --web上傳
  TakeOut = 10, --web取出
  Preview = 11, --web預覽
}

ESoulEffect = {
  Compound = 0,
  Success = 1
}

UISoul = {}
local this = UISoul;

this.name = "UISoul";
this.uiController = nil;

local transform_FrameRect;
local gameObject_Left;

--bag

--Equip
local gameObject_equip;
local gameObject_skillRoot;
local gameObject_Detail;
local gameObject_DetailRoot;
local skills = {};
local uiCareer;
local jmpDrawer_Role;
local image_equip;
local image_UpgradeLv;
local image_Prev;
local image_Next;
local text_Name;
local text_Lv;
local text_LvStr;
local text_UpgradeLv;

--Left
local compoundCube = {};
local buttons = {};
local gameObject_Result = {};
local image_Star = {};
local image_questionMark;
local gameObject_plus;
local gameObject_Star;
local gameObject_SkillLv;
local gameObject_AttInfo;
local mainSkills = {};
local subSkills = {};
local beforeSixAtt = {};
local afterSixAtt = {};
local rawImage_Mixed;
local text_reelQuant;
local text_coinQuant;
local text_TransHint;

--var
local currentRole = nil;
local currentChosenSoul = nil;
local soulBeforeSwitch = nil;
local intCount = 0;
local followIndex = 1;
local currentTag = ESoulTag.Equip;
local showDetail = false;
local switchEquip = false;
local isInUse = false;
local hasResult = false;
local reelOver = false;
local coinOver = false;
local isPlayingAnime = false;

local tagData = {};
local npcList = {};
local usingItemInfo = {};
local affSoul = {};

function UISoul.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  this.uiController.onClose = this.OnClose;

  --UI
  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  local tempEvent;

    --region equip
  gameObject_equip = uiController:FindGameObject("Equip");
  image_Prev =  uiController:FindGameObject("Image_Prev");
  image_Next =  uiController:FindGameObject("Image_Next");
  image_equip =  UIItemController.New(uiController:FindGameObject("Image_EquipBG").transform);
  uiCareer = UICareerController.New(uiController:FindGameObject("GameObject_Career"));
  gameObject_Detail = uiController:FindGameObject("Image_DetailBG");
  gameObject_DetailRoot = UIStatusDetail.New(uiController:FindGameObject("Image_DetailBG"));
  gameObject_skillRoot = uiController:FindGameObject("SkillRoot");

  for i = 1, Role.maxSoulSkill do
    local skill = {};
    skill.gameObject = gameObject_skillRoot.transform:Find(string.Concat("Skill(", i, ")")).gameObject;
    skill.transform = skill.gameObject.transform;
    skill.rawImage_Icon = skill.gameObject.transform:GetComponent("RawImage");
    skill.icon = UISkillController.New(skill.transform);

    skills[i] = skill;
  end

  image_UpgradeLv = uiController:FindImage("Image_UpgradeLv");
  jmpDrawer_Role = uiController:FindRawImage("RawImage_Role").gameObject:AddComponent(typeof(JmpDrawer));

  text_UpgradeLv = uiController:FindText("Text_UpgradeLv");
  text_Name = uiController:FindText("Text_Name");
  text_Lv = uiController:FindText("Text_Lv");
  text_LvStr = uiController:FindText("Text_T9_");

  tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = -1;

  tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = 1;

  tempEvent = uiController:FindEvent("Image_DetailPrev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = -1;

  tempEvent = uiController:FindEvent("Image_DetailNext");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchRole);
  tempEvent.parameter = 1;

  tempEvent = uiController:FindEvent("Image_Detail");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Detail);
  --endregion

    --region left
  gameObject_Left = uiController:FindGameObject("Left");

  local gameObject_btn = uiController:FindGameObject("Buttons").transform;
  for i = 1, 3 do
    compoundCube[i] = {};
    compoundCube[i].gameObject = uiController:FindGameObject(string.Concat("Image_CompoundBG (",i,")"));
    compoundCube[i].transform = compoundCube[i].gameObject.transform;
    compoundCube[i].item = UIItemController.New(compoundCube[i].transform);
    compoundCube[i].text_Hint = compoundCube[i].transform:Find("Text_Hint"):GetComponent("Text");
    compoundCube[i].image_Arrow = compoundCube[i].transform:Find("Image_Arrow").gameObject;

    buttons[i] = {};
    buttons[i].gameObject = gameObject_btn:Find(string.Concat("Image_Func",i)).gameObject;
    buttons[i].event = buttons[i].gameObject:GetComponent("UIEvent");
    buttons[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Button);
    buttons[i].event.parameter = i;
    buttons[i].text_Hint = buttons[i].gameObject.transform:Find("Text_Submit"):GetComponent("Text");
  end

  gameObject_Result.gameObject = uiController:FindGameObject("Image_CompoundBG (4)");
  gameObject_Result.item = UIItemController.New(gameObject_Result.gameObject.transform);
  gameObject_plus = uiController:FindGameObject("PlusImage");
  gameObject_Star = uiController:FindGameObject("Star");
  gameObject_SkillLv = uiController:FindGameObject("SkillPanel");
  gameObject_AttInfo = uiController:FindGameObject("AttInfo");

  for i = 1, 5 do
    image_Star[i] = gameObject_Star.transform:Find(string.Concat("Image_Star (", i, ")")).gameObject;
  end

  for i = 1, 6 do
    beforeSixAtt[i] = gameObject_AttInfo.transform:Find("Before"):Find(string.Concat("Text_Before_", i)):GetComponent("Text");

    afterSixAtt[i] = {};
    afterSixAtt[i].text_Att = gameObject_AttInfo.transform:Find("After"):Find(string.Concat("Text_After_", i)):GetComponent("Text");
    afterSixAtt[i].image_Arrow = gameObject_AttInfo.transform:Find("After"):Find(string.Concat("Text_After_", i)):Find(string.Concat("UpdateInfo_", i)):Find(string.Concat("Image_Direct_", i)):GetComponent("Image");
  end

  local mainRoot = uiController:FindGameObject("MainSkillRoot");
  local subRoot = uiController:FindGameObject("SubSkillRoot");

  for i = 1, Role.maxSoulSkill do
    local skill = {};
    skill.gameObject = mainRoot.transform:Find(string.Concat("Skill(", i, ")")).gameObject;
    local transform = skill.gameObject.transform;
    skill.rawImage_Icon = transform:Find("RawImage_icon");
    skill.text_Name = transform:Find("Text_Name"):GetComponent("Text");
    skill.text_Lv = transform:Find("LvArea"):Find("Text_Lv"):GetComponent("Text");
    skill.image_Arrow = transform:Find("LvArea"):Find("Image_Direct_2"):GetComponent("Image");
    skill.image_UnUse = transform:Find("Image_UnUse"):GetComponent("Image");
    skill.hasSame = false;
    skill.icon = UISkillController.New(skill.rawImage_Icon);

    mainSkills[i] = skill;
  end

  for i = 1, Role.maxSoulSkill do
    local skill = {};
    skill.gameObject = subRoot.transform:Find(string.Concat("Skill(", i, ")")).gameObject;
    local transform = skill.gameObject.transform;
    skill.rawImage_Icon = transform:Find("RawImage_icon");
    skill.text_Name = transform:Find("Text_Name"):GetComponent("Text");
    skill.text_Lv = transform:Find("Text_Lv"):GetComponent("Text");
    skill.image_UnUse = transform:Find("Image_UnUse"):GetComponent("Image");
    skill.hasSame = false;
    skill.icon = UISkillController.New(skill.rawImage_Icon);

    subSkills[i] = skill;
  end

  image_questionMark = uiController:FindImage("Image_QuestionMark");

  rawImage_Mixed = uiController:FindRawImage("RawImage_Mixed");
  rawImage_Mixed.enabled = false;

  text_reelQuant = uiController:FindText("Text_ReelQuant");
  text_coinQuant = uiController:FindText("Text_CoinQuant");
  text_TransHint = uiController:FindText("Text_TransHint");
    --endregion
end

function UISoul.OnOpen()
  currentTag = ESoulTag.Compound;

  npcList = Soul.GetNpcList();
  this.ClearData();
  this.ResetValues();

  return true;
end

function UISoul.OnVisible(visible)
  if visible then
    tagData[ESoulTag.Compound] = { string.Get(50332), nil };
    tagData[ESoulTag.Equip] = { string.Get(20165), nil };
    tagData[ESoulTag.Intensify] = { string.Get(20029), nil };
    tagData[ESoulTag.Breakthrough] = { string.Get(10601), nil };
    tagData[ESoulTag.SkillLv] = { string.Get(23680), nil };
    tagData[ESoulTag.Affination] = { string.Get(23048), nil };

    UIFrame.Active(transform_FrameRect, string.Get(23679), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagData, currentTag, 1);

    this.UpdateUI();
  end
end

function UISoul.OnClose()
  jmpDrawer_Role:Disable();
  this.ResetValues();
  this.ClearData();
  gameObject_Detail:SetActive(showDetail);

  if UI.IsVisible(UISoul) then
    UI.Close(UISoul);
  end

  if UI.IsVisible(UIBag) then
    UI.Close(UIBag);
  end
end

function UISoul.OnClick_Tag(tag)
  if isPlayingAnime then return false end
  if (tag == ESoulTag.Equip) then
    local followNpcs = Role.GetFollowNpcs(Role.playerId);
    if table.Count(followNpcs) == 0 then
      ShowCenterMessage(string.Get(25092)); -- 請先攜帶武將後，再進行裝備
      return false
    end
  end

  currentTag = tag;
  this.ResetValues();
  UIBag.SkidBlink();

  if UI.IsVisible(UIBag) then
    UI.Close(UIBag)
  end

  this.ClearData();
  this.UpdateUI();

  return true;
end

function UISoul.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 0, this, string.Get(23679), string.Get(23837));
  end

  return true;
end

function UISoul.OnClick_Close()
  currentTag = ESoulTag.Compound;
  UI.Close(UISoul);

  return true;
end

  --region Update
function UISoul.UpdateUI()
  gameObject_equip:SetActive(true);
  gameObject_Left:SetActive(currentTag ~= ESoulTag.Equip);
  gameObject_plus:SetActive(currentTag == ESoulTag.Affination);
  gameObject_Star:SetActive(currentTag == ESoulTag.Breakthrough);
  gameObject_SkillLv:SetActive(currentTag == ESoulTag.SkillLv);
  gameObject_AttInfo:SetActive(currentTag == ESoulTag.Affination);
  compoundCube[2].gameObject:SetActive(currentTag ~= ESoulTag.Breakthrough and currentTag ~= ESoulTag.SkillLv);

  for i = 1, 3 do
    compoundCube[i].image_Arrow:SetActive(currentTag ~= ESoulTag.Affination and currentTag ~= ESoulTag.SkillLv);
  end

  gameObject_Result.gameObject:SetActive(currentTag ~= ESoulTag.Affination and currentTag ~= ESoulTag.SkillLv);

  this.UpdateCompound();
  this.SetButtonsAndHint();
  this.UpdateEquip();
  gameObject_equip:SetActive(currentTag == ESoulTag.Equip);
  this.UpdateIntensify();
  this.UpdateBreakthrough();
  this.UpdateSkillLv();
  this.UpdateAffination();
  this.UpdateOpenBag();
  UIBag.UpdateUI();
end

function UISoul.UpdateOpenBag()
  if currentTag == ESoulTag.Equip then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.Equip, function()
          this.OpenBagEquip();
        end);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave, currentTag) then return false end
        if currentRole ~= nil then
          if currentRole:GetAttribute(EAttribute.Element) ~= itemSave.element then return false end
        end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  elseif currentTag == ESoulTag.Compound then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.Compound, function()
          this.OpenBagCompound(bagIndex);
        end, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave, currentTag) then return false end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  elseif currentTag == ESoulTag.Intensify then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.Intensify, function()
          this.OpenBagIntensify(bagIndex);
        end, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave, currentTag) then return false end
        if itemSave.lv == itemSave:GetLimitLv() then return false end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  elseif currentTag == ESoulTag.Breakthrough then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.Breakthrough, function()
          this.OpenBagBreakthrough(bagIndex);
        end, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave, currentTag) then return false end
        if itemSave.starLv == soulDatas[itemSave.quality].limitBreakthrough then return false end
        if usingItemInfo[1] ~= nil then
          if itemSave.Id ~= usingItemInfo[1].itemId then return false end
        end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  elseif currentTag == ESoulTag.SkillLv then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.SkillLv, function()
          this.OpenBagSkillLv(bagIndex);
        end, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave, currentTag) then return false end
        if usingItemInfo[1] ~= nil then
          for _, v in pairs(usingItemInfo[1].soulData.skill) do
            for _, m in pairs(itemSave.skill) do
              if v.id == m.id and v.id ~= 0 and m.id ~= 0 then
                return true;
              end
            end
          end

          return false;
        end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  elseif currentTag == ESoulTag.Affination then
    UI.Open(UIBag,
      function(bagIndex, itemId)
        currentChosenSoul = Item.GetBagItem(EThings.Soul, bagIndex);
        UI.Open(UISoulInfo, currentChosenSoul, ESoulAction.Affination, function()
          this.OpenBagAffination(bagIndex);
        end, bagIndex);
      end,
      Vector2.New(260, -25),
      false,
      nil,
      function(itemSave)
        if not Soul.CheckItemKind(itemSave) then return false end
        if itemSave.lv <= 1 then return false end

        return true;
      end,
      false,
      false,
      EThings.Soul
    );
  end
end

function UISoul.UpdateEquip()
  if currentTag ~= ESoulTag.Equip then return end

  if currentRole == nil then
    currentRole = npcList.role[1];
    followIndex = 1;
  end

  --region Role
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
    if Define.IsInternal() and UIDebug.RecordMode then
      text_Name.text = string.gsub(text_Name.text, "%d%d%d%d%d", "");
    end

    text_Lv.text = currentRole:GetAttribute(EAttribute.Lv);
    text_LvStr.text = string.Get(71154);

    if currentRole.data.upgradeLv > 0 then
      text_UpgradeLv.transform.parent.gameObject:SetActive(true);
      text_UpgradeLv.text = currentRole.data.upgradeLv;
      image_UpgradeLv.sprite = UI.GetUpgradeLvIcon(UIStatus.CheckDataGetUpgradeLvIcon(currentRole.data.upgradeLv));
    else
      text_UpgradeLv.transform.parent.gameObject:SetActive(false);
    end

    this.ShowRoleEquip();  --武將身上穿的
    this.UpdateDetail();
  else
    jmpDrawer_Role:Disable();
    text_Name.text = "";
    text_Lv.text = "";
    text_LvStr.text = "";
    text_UpgradeLv.transform.parent.gameObject:SetActive(false);
    image_equip:SetItem(0);
  end
  --endregion

  --region skill
  local equipSoul = nil;
  if currentRole ~= nil then
    equipSoul = Soul.GetEquippedSoul(currentRole.npcId);
  end

  if equipSoul ~= nil then
    gameObject_skillRoot.gameObject:SetActive(true);

    local skillData;
    for index = 1, Role.maxSoulSkill do
      local id = equipSoul.skill[index].id;
      skillData = skillDatas[id];  --靈魂技能紀錄
      if skillData ~= nil then
        if skills[index].icon == nil then
          skills[index].icon = UISkillController.New(skills[index].transform);
        end

        local skillController = skills[index].icon;
        skillController:SetSkill(skillData.id, equipSoul:GetSkillLv(id));
        skillController:SetShowCost(false);
        skillController:SetEvent(this.OnClick_Skill);
        skillController.transform.localPosition = Vector3.New(0, 0, 0);

        skills[index].gameObject:SetActive(true);
      else
        skills[index].gameObject:SetActive(false);
      end
    end
  else
    gameObject_skillRoot.gameObject:SetActive(false);
  end
  --endregion
end

function UISoul.UpdateCompound()
  if currentTag ~= ESoulTag.Compound then return end

  text_reelQuant.text = "";
  text_coinQuant.text = "";

  for i = 1, 3 do
    if usingItemInfo[i] ~= nil then
      local itemSave = Item.GetBagItem(EThings.Soul, usingItemInfo[i].bagIndex);

      if itemSave ~= nil then
        compoundCube[i].item:SetItem(itemSave.Id, 1, itemSave);
        compoundCube[i].item:SetEvent(function()
          UI.Open(UISoulInfo, itemSave, ESoulAction.MoveOut, function()
            if i == 1 then
              this.ClearData();
              currentChosenSoul = nil;
              isInUse = false;
            else
              Item.ClearAllInUse();
              usingItemInfo[i] = nil;

              for _, v in pairs(usingItemInfo) do
                if v.bagIndex ~= nil then
                  Item.SetItemInUse(v.bagIndex);
                end
              end
            end

            UI.Close(UISoulInfo);
            this.UpdateUI();
          end);
        end);
      end
    else
      compoundCube[i].item:SetItem(0);
      compoundCube[i].item:SetEvent();
    end
  end

  if not hasResult then
    gameObject_Result.item:SetItem(0);
    gameObject_Result.item:SetEvent();
    image_questionMark.enabled = true;
  end

  this.StopMixEffect();
end

function UISoul.UpdateIntensify()
  if currentTag ~= ESoulTag.Intensify then return end

  if usingItemInfo[1] ~= nil then
    local soulSave = Item.GetBagItem(EThings.Soul, usingItemInfo[1].bagIndex);
    local soulData = soulLevelDatas[soulSave.lv];

    this.SetIntAffUsing(soulSave);

    text_reelQuant.text = string.Concat(soulData:GetLevelData(soulSave.quality).reel, "/", usingItemInfo[2].quant);
    text_coinQuant.text = string.Concat(usingItemInfo[3].quant, "/", Role.GetPlayerCurrency(ECurrency.TS));

    this.SetIntAffCube(soulSave);
  else
    text_reelQuant.text = "";
    text_coinQuant.text = "";

    for i = 1, 3 do
      compoundCube[i].item:SetItem(0);
      compoundCube[i].item:SetEvent();
    end
  end

  if not hasResult then
    gameObject_Result.item:SetItem(0);
    gameObject_Result.item:SetEvent();
    image_questionMark.enabled = true;
  end
end

function UISoul.UpdateBreakthrough()
  if currentTag ~= ESoulTag.Breakthrough then return end

  text_coinQuant.text = "";
  text_reelQuant.text = "";

  if not hasResult then
    gameObject_Result.item:SetItem(0);
    gameObject_Result.item:SetEvent();
    image_questionMark.enabled = true;

    for i = 1, table.Count(image_Star) do
      if image_Star[i] ~= nil then
        image_Star[i]:SetActive(false);
      end
    end
  end

  for i = 1, 3 do
    if usingItemInfo[i] ~= nil then
      local itemSave = Item.GetBagItem(EThings.Soul, usingItemInfo[i].bagIndex);

      if itemSave ~= nil then
        compoundCube[i].item:SetItem(itemSave.Id, 1, itemSave);
        compoundCube[i].item:SetEvent(function()
          UI.Open(UISoulInfo, itemSave, ESoulAction.MoveOut, function()
            if i == 1 then
              this.ClearData();
              currentChosenSoul = nil;
              isInUse = false;
            else
              Item.ClearAllInUse();
              usingItemInfo[i] = nil;

              for _, v in pairs(usingItemInfo) do
                if v.bagIndex ~= nil then
                  Item.SetItemInUse(v.bagIndex);
                end
              end
            end

            UI.Close(UISoulInfo);
            this.UpdateUI();
          end);
        end);
      end
    else
      compoundCube[i].item:SetItem(0);
      compoundCube[i].item:SetEvent();
    end
  end
end

function UISoul.UpdateSkillLv()
  if currentTag ~= ESoulTag.SkillLv then return end

  text_coinQuant.text = "";
  text_reelQuant.text = "";

  for i = 1, 3 do
    if usingItemInfo[i] ~= nil then
      local itemSave = Item.GetBagItem(EThings.Soul, usingItemInfo[i].bagIndex);

      if itemSave ~= nil then
        compoundCube[i].item:SetItem(itemSave.Id, 1, itemSave);
        compoundCube[i].item:SetEvent(function()
          UI.Open(UISoulInfo, itemSave, ESoulAction.MoveOut, function()
            if i == 1 then
              this.ClearData();
              currentChosenSoul = nil;
              isInUse = false;
            else
              Item.ClearAllInUse();
              usingItemInfo[i] = nil;

              for _, v in pairs(usingItemInfo) do
                if v.bagIndex ~= nil then
                  Item.SetItemInUse(v.bagIndex);
                end
              end
            end

            UI.Close(UISoulInfo);
            this.UpdateUI();
          end);
        end);
      end
    else
      compoundCube[i].item:SetItem(0);
      compoundCube[i].item:SetEvent();
    end
  end

  if usingItemInfo[3] ~= nil then
    usingItemInfo[1].show = {};
    usingItemInfo[3].show = {};

    for i = 1, table.Count(usingItemInfo[1].soulData.skill) do
      for j = 1, table.Count(usingItemInfo[3].soulData.skill) do
        if usingItemInfo[1].soulData.skill[i].id == usingItemInfo[3].soulData.skill[j].id then
          usingItemInfo[1].show[i] = false;
          usingItemInfo[3].show[j] = false;
        else
          if usingItemInfo[3].show[j] == nil then
            usingItemInfo[3].show[j] = true;
          end
        end
      end

      if usingItemInfo[1].show[i] == nil then
        usingItemInfo[1].show[i] = true;
      end
    end
  end

  for index = 1, Role.maxSoulSkill do
    if usingItemInfo[1] ~= nil then
      local id = usingItemInfo[1].soulData.skill[index].id;
      local skillData = skillDatas[id];  --靈魂技能紀錄
      if skillData ~= nil then
        Soul.SetAffSkills(id, mainSkills[index], usingItemInfo[1].soulData);

        mainSkills[index].text_Name.text = skillData.name;

        if usingItemInfo[3] ~= nil then
          mainSkills[index].image_UnUse.enabled = usingItemInfo[1].show[index];
          mainSkills[index].image_Arrow.enabled = not usingItemInfo[1].show[index];

          if not usingItemInfo[1].show[index] then
            if usingItemInfo[1].soulData:SkillIsMax(id) then
              mainSkills[index].text_Lv.text = string.Concat(string.Get(22447), string.Get(21453));
              mainSkills[index].image_Arrow.enabled = false;
            else
              if usingItemInfo[1].soulData:GetSkillLv(id) + 1 == skillData.maxLv then
                mainSkills[index].text_Lv.text = string.Concat(string.Get(22447), string.Get(21453));
              elseif usingItemInfo[3].soulData:GetSkillLv(id) > usingItemInfo[1].soulData:GetSkillLv(id) then
                mainSkills[index].text_Lv.text = string.Concat(string.Get(22447), usingItemInfo[3].soulData:GetSkillLv(id) + 1);
              else
                mainSkills[index].text_Lv.text = string.Concat(string.Get(22447), usingItemInfo[1].soulData:GetSkillLv(id) + 1);
              end
            end

            mainSkills[index].text_Lv.color = Color.Red;
          else
            mainSkills[index].text_Lv.text = "";
          end
        else
          mainSkills[index].image_UnUse.enabled = false;
          mainSkills[index].image_Arrow.enabled = false;
          mainSkills[index].text_Lv.text = "";
        end

        mainSkills[index].gameObject:SetActive(true);
      else
        mainSkills[index].gameObject:SetActive(false);
      end
    else
      mainSkills[index].gameObject:SetActive(false);
    end
  end

  for index = 1, Role.maxSoulSkill do
    if usingItemInfo[3] ~= nil then
      local id = usingItemInfo[3].soulData.skill[index].id;
      local skillData = skillDatas[id];  --靈魂技能紀錄
      if skillData ~= nil then
        Soul.SetAffSkills(id, subSkills[index], usingItemInfo[3].soulData);

        subSkills[index].text_Name.text = skillData.name;
        subSkills[index].text_Lv.text = "";
        subSkills[index].image_UnUse.enabled = usingItemInfo[3].show[index];

        subSkills[index].gameObject:SetActive(true);
      else
        subSkills[index].gameObject:SetActive(false);
      end
    else
      subSkills[index].gameObject:SetActive(false);
    end
  end
end

function UISoul.UpdateAffination()
  if currentTag ~= ESoulTag.Affination then return end

  if affSoul.soul ~= nil then
    usingItemInfo[1] = affSoul.soul;

    this.SetIntAffUsing(usingItemInfo[1].soulData);
    Item.SetItemInUse(usingItemInfo[1].bagIndex);

    hasResult = true;
  end

  if usingItemInfo[1] ~= nil then
    local soulSave = Item.GetBagItem(EThings.Soul, usingItemInfo[1].bagIndex);

    text_reelQuant.text = string.Concat(1, "/", usingItemInfo[2].quant);
    text_coinQuant.text = string.Concat(usingItemInfo[3].quant, "/",Role.GetPlayerCurrency(ECurrency.TS));
    text_TransHint.text = string.Concat(string.format(string.Get(24056), soulSave:GetConvertPercent()), string.GetColorText(string.Get(24064), Color.Yellow));

    this.SetIntAffCube(soulSave);

    for i = 1, 6 do
      local temp = string.Concat(" (", soulSave:GetConvertSixAtt(i), ")");
      beforeSixAtt[i].text = string.Concat(soulSave:GetSixAtt(i), string.GetColorText(temp, Color.Yellow));
    end

    if hasResult and affSoul.sixAtt ~= nil then
      for i = 1, 6 do
        local temp = affSoul.sixAtt[i] - soulSave.nowAtt[i] + soulSave:GetSixAtt(i);
        afterSixAtt[i].text_Att.text = string.Concat(temp, " (", math.floor(temp * soulSave:GetConvertPercent(), ")"));
        afterSixAtt[i].image_Arrow.enabled = affSoul.sixAtt[i] - soulSave.nowAtt[i] > 0 or affSoul.sixAtt[i] - soulSave.nowAtt[i] < 0;

        if affSoul.sixAtt[i] - soulSave.nowAtt[i] > 0 then
          afterSixAtt[i].image_Arrow.sprite = UI.GetSprite("com107");
        elseif affSoul.sixAtt[i] - soulSave.nowAtt[i] < 0 then
          afterSixAtt[i].image_Arrow.sprite = UI.GetSprite("com108");
        end

        if Define.IsDebugMode() and not UIDebug.RecordMode then
          afterSixAtt[i].text_Att.text = string.Concat(afterSixAtt[i].text_Att.text, " (" ,affSoul.sixAtt[i],  " - ",  soulSave.nowAtt[i], " = ", affSoul.sixAtt[i] - soulSave.nowAtt[i], ")");
        end
      end
    end
  else
    text_reelQuant.text = "";
    text_coinQuant.text = "";
    text_TransHint.text = "";

    for i = 1, 3 do
      compoundCube[i].item:SetItem(0);
      compoundCube[i].item:SetEvent();
    end

    for i = 1, 6 do
      beforeSixAtt[i].text = "";
      afterSixAtt[i].text_Att.text = "";
      afterSixAtt[i].image_Arrow.enabled = false;
    end
  end

  this.SetButtonsAndHint();
end

function UISoul.UpdateStayAff()
  this.ClearData();
  this.ClearAffInfo();
  hasResult = false;
  this.UpdateUI();
end
  --endregion

function UISoul.CheckAff(soul)
  if affSoul.soul == nil then return false end
  if affSoul.soul.soulData.sn == soul.sn then
    return true;
  end
end

function UISoul.ClearData()
  Item.ClearAllInUse();
  table.Clear(usingItemInfo);

  for i = 1, Role.maxSoulSkill do
    if skills[i].icon ~= nil then
      skills[i].icon:Destroy();
      skills[i].icon = nil;
    end

    if mainSkills[i].icon ~= nil then
      mainSkills[i].icon:SetSize(1);
      mainSkills[i].icon:Destroy();
      mainSkills[i].icon = nil;
    end

    if subSkills[i].icon ~= nil then
      subSkills[i].icon:SetSize(1);
      subSkills[i].icon:Destroy();
      subSkills[i].icon = nil;
    end
  end
end

function UISoul.ClearAffInfo()
  table.Clear(affSoul);
end

  --region receive
function UISoul.ReceiveEquip(npcId, sn)
  local bagIndex = 1;
  local bag = Item.GetBag(EThings.Soul)
  local soul;

  for k, v in pairs(bag) do
    if v.sn == sn then
      soul = v;
      bagIndex = k;
      break;
    end
  end

  if soul ~= nil then
    soul.equipId = npcId;

    Item.DelBagItem(EThings.Soul, bagIndex)
    Item.SetBagItem(EThings.EquippedSoul, Soul.GetEmptyIndex(EThings.EquippedSoul), soul);

    Soul.ShowEquipMessage(currentRole, soul.Id, true);

    switchEquip = false;
    soulBeforeSwitch = nil;
    currentChosenSoul = nil;
    UI.Close(UISoulInfo);
    UI.Close(UIBag);
    this.UpdateUI();
  else
    logError("sn error")
  end
end

function UISoul.ReceiveTakeOff(npcId, sn)
  local bag = Item.GetBag(EThings.EquippedSoul);
  local index = 0;
  local soul;

  for k, v in pairs(bag) do
    if v.sn == sn then
      soul = v;
      break;
    end
  end

  if soul == nil then return end

  for i = 1, Item.GetBagCount(EThings.EquippedSoul) do
    if bag[i] ~= nil and bag[i].equipId == npcId then
      index = i;
      break;
    end
  end

  Item.DelBagItem(EThings.EquippedSoul, index)

  soul.equipId = 0;

  Item.SetBagItem(EThings.Soul, Soul.GetEmptyIndex(EThings.Soul), soul);

  local equipSoul = soulBeforeSwitch;

  if equipSoul ~= nil and currentChosenSoul.sn ~= equipSoul.sn then
    switchEquip = true;
    this.SendEquip(1, currentChosenSoul)
  else
    currentChosenSoul = nil;
    UI.Close(UISoulInfo);
    UI.Close(UIBag);
    if UI.IsVisible(UISoul) then
      UISoul.UpdateUI();
    end
  end

  Soul.ShowEquipMessage(currentRole, soul.Id, false);
end

function UISoul.ReceiveCompound(soulData)
  for i = 1, 3 do
    Item.DelBagItem(EThings.Soul, usingItemInfo[i].bagIndex);
    Chat.AddMessage(EChannel.System, string.format(string.Get(21160), itemDatas[usingItemInfo[i].itemId]:GetName(true), 1));
  end

  Item.ClearAllInUse();

  local index = Soul.GetEmptyIndex(EThings.Soul);
  Item.SetBagItem(EThings.Soul, index, soulData);

  this.ClearData();

  this.UpdateCompound();

  UIBag.SetBlink(index, 2);

  this.StartMixEffect(ESoulEffect.Compound, soulData);
end

function UISoul.ReceiveIntensify(soulData)
  this.StartMixEffect(ESoulEffect.Success);
  Item.SetBagItem(EThings.Soul, usingItemInfo[1].bagIndex, soulData);
  UIBag.SetBlink(usingItemInfo[1].bagIndex, 0);

  gameObject_Result.item:SetItem(soulData.Id, 1, soulData);
  gameObject_Result.item:SetEvent(function()
    UI.Open(UISoulInfo, soulData);
  end
  );

  hasResult = true;
  image_questionMark.enabled = false;

  usingItemInfo[2].quant = Item.GetItemCount(29801);

  Soul.ShowIntensifyAttMsg(soulData.nowAtt, usingItemInfo[1].soulData.nowAtt);

  if soulData.lv == soulData:GetLimitLv() then
    table.Clear(usingItemInfo);
    Item.ClearAllInUse();
  end

  this.UpdateIntensify();

  if reelOver then
    ShowCenterMessage(string.Get(23685));
    return;
  end

  if coinOver then
    ShowCenterMessage(string.Get(23686));
    return;
  end

  if soulData.lv == soulData:GetLimitLv() then
    ShowCenterMessage(string.Get(23699));
    return;
  end

  if intCount == 1 then
    ShowCenterMessage(string.Get(23687));
  elseif intCount == 10 then
    ShowCenterMessage(string.Get(23693));
  end

  usingItemInfo[1].soulData = soulData;

  reelOver = false;
  coinOver = false;
  currentChosenSoul = nil;
end

function UISoul.ReceiveBreakThrough(sn, starLv)
  if sn == usingItemInfo[1].soulData.sn then
    usingItemInfo[1].soulData.starLv = starLv;
    Item.SetBagItem(EThings.Soul, usingItemInfo[1].bagIndex, usingItemInfo[1].soulData);
    Item.DelBagItem(EThings.Soul, usingItemInfo[3].bagIndex);
    gameObject_Result.item:SetItem(usingItemInfo[1].itemId, 1, usingItemInfo[1].soulData);
    local temp = Item.GetBagItem(EThings.Soul, usingItemInfo[1].bagIndex);
    gameObject_Result.item:SetEvent(function()
      UI.Open(UISoulInfo, temp);
    end);

    for i = 1, starLv do
      if image_Star[i] ~= nil then
        image_Star[i]:SetActive(true);
      end
    end

    UIBag.SetBlink(usingItemInfo[1].bagIndex, 0);
    image_questionMark.enabled = false;
    hasResult = true;
    currentChosenSoul = nil;
    this.ClearData();
    this.UpdateBreakthrough();

    ShowCenterMessage(string.Get(23688));
    this.StartMixEffect(ESoulEffect.Success);
  end
end

function UISoul.ReceiveSkillLv(soulData)
  Item.SetBagItem(EThings.Soul, usingItemInfo[1].bagIndex, soulData);
  Item.DelBagItem(EThings.Soul, usingItemInfo[3].bagIndex)

  for i = 1, table.Count(usingItemInfo[1].show) do
    if not usingItemInfo[1].show[i] then
      local id = soulData.skill[i].id;
      local skillData = skillDatas[id];
      if skillData ~= nil and not usingItemInfo[1].soulData:SkillIsMax(id) then
        ShowCenterMessage(string.format(string.Get(23689), skillData.name, soulData:GetSkillLv(id)));
      end
    end
  end

  UIBag.SetBlink(usingItemInfo[1].bagIndex, 0);

  this.ClearData();
  this.UpdateSkillLv();
end

function UISoul.ReceiveAffination(kind, sn, sixAtt)
  if sn ~= usingItemInfo[1].soulData.sn then
    logError("sn false")
    return;
  end

  local soulData = usingItemInfo[1].soulData;

  if kind == 1 then
    hasResult = true;

    affSoul.soul = {};
    affSoul.soul = usingItemInfo[1];

    affSoul.sixAtt = {};
    affSoul.sixAtt = sixAtt;

    usingItemInfo[2].quant = Item.GetItemCount(29802);
    this.UpdateAffination();
  elseif kind == 2 then
    for i = 1, 6 do
      local temp = sixAtt[i] - soulData.nowAtt[i];
      soulData.nowAtt[i] = soulData.nowAtt[i] + temp;
    end

    Item.SetBagItem(EThings.Soul, usingItemInfo[1].bagIndex, soulData);
    UIBag.SetBlink(usingItemInfo[1].bagIndex, 0);
    this.ClearData();
    this.ClearAffInfo();
    hasResult = false;
    this.UpdateAffination();
  end
end
  --endregion

function UISoul.ResetValues()
  currentRole = nil;
  currentChosenSoul = nil;
  intCount = 0;
  showDetail = false;
  isInUse = false;
  hasResult = false;
  reelOver = false;
  coinOver = false;
  isPlayingAnime = false;
  switchEquip = false;
  soulBeforeSwitch = nil;
end

  --region send
--C:120-001 <裝備靈魂> Kind(1) + 武將ID(2) + 跟隨武將索引(1) + SN(8) Kind=1.裝備 2.卸下
function UISoul.SendEquip(kind, soul)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if not switchEquip then
    soulBeforeSwitch = Soul.GetEquippedSoul(currentRole.npcId);
  end

  Soul.SaveAttribute(currentRole, switchEquip);

  this.SendEquipSoul(kind, currentRole.npcId, Soul.GetFollowIndex(npcList, currentRole), soul.sn);
end

function UISoul.UnEquip(kind, soul, followIndex)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  this.SendEquipSoul(kind, soul.equipId, followIndex, soul.sn);
end

function UISoul.SendEquipSoul(kind, equipId, followIndex, sn)
  sendBuffer:Clear();
  sendBuffer:WriteByte(kind);
  sendBuffer:WriteUInt16(equipId);
  sendBuffer:WriteByte(followIndex);
  sendBuffer:WriteInt64(sn);
  Network.Send(120, 1, sendBuffer);
end

--C:120-002 合成 SN1(8) + SN2(8) + SN3(8)
function UISoul.SendCompound()
  sendBuffer:Clear();
  sendBuffer:WriteInt64(usingItemInfo[1].soulData.sn);
  sendBuffer:WriteInt64(usingItemInfo[2].soulData.sn);
  sendBuffer:WriteInt64(usingItemInfo[3].soulData.sn);
  Network.Send(120, 2, sendBuffer);
end

--C:120-003 <強化靈魂> 使用次數(1) + SN(8)
function UISoul.SendIntensify(times)
  sendBuffer:Clear();
  sendBuffer:WriteByte(times);
  sendBuffer:WriteInt64(usingItemInfo[1].soulData.sn);
  Network.Send(120, 3, sendBuffer);
end

--C:120-004 <突破靈魂> 主靈魂SN(8) + 材料SN(8)
--C:120-005 <技能等級靈魂> 主靈魂SN(8) + 材料SN(8)
function UISoul.SendBreakthroughOrSkillLv()
  sendBuffer:Clear();
  sendBuffer:WriteInt64(usingItemInfo[1].soulData.sn);
  sendBuffer:WriteInt64(usingItemInfo[3].soulData.sn);
  if currentTag == ESoulTag.Breakthrough then
    Network.Send(120, 4, sendBuffer);
  elseif currentTag == ESoulTag.SkillLv then
    Network.Send(120, 5, sendBuffer);
  end
end

--C:120-006 <洗鋉靈魂> Kind(1) +
--                            1.洗鋉數值 + SN(8)
--                            2.確定洗鍊 + SN(8)
--                            3.保留 + SN(8)
function UISoul.SendAffination(kind)
  sendBuffer:Clear();
  sendBuffer:WriteByte(kind);
  sendBuffer:WriteInt64(usingItemInfo[1].soulData.sn);
  Network.Send(120, 6, sendBuffer);
end
  --endregion

function UISoul.SetSoulDataToUsing(bagIndex, infoIndex, data)
  usingItemInfo[infoIndex] = {};
  usingItemInfo[infoIndex].soulData = {};
  usingItemInfo[infoIndex].soulData = data;
  usingItemInfo[infoIndex].bagIndex = bagIndex;
  usingItemInfo[infoIndex].itemId = data.Id;
  usingItemInfo[infoIndex].element = data.element;
  usingItemInfo[infoIndex].quality = data.quality;

  Item.SetItemInUse(bagIndex);
end

function UISoul.SetIntAffUsing(itemSave)
  local soulData = soulLevelDatas[itemSave.lv];
  local using2Id = 0;
  local using3Quant = 0;

  if currentTag == ESoulTag.Intensify then
    using2Id = 29801;
    using3Quant = soulData:GetLevelData(itemSave.quality).coin;
  elseif currentTag == ESoulTag.Affination then
    using2Id = 29802;
    using3Quant = soulData:GetLevelData(itemSave.quality).affCoin;
  end

  usingItemInfo[2] = {};
  usingItemInfo[2].bagIndex = 0;
  usingItemInfo[2].itemId = using2Id;
  usingItemInfo[2].quant = Item.GetItemCount(using2Id);

  usingItemInfo[3] = {};
  usingItemInfo[3].itemId = 65343;
  usingItemInfo[3].quant = using3Quant;
end

function UISoul.SetIntAffCube(soulSave)
  compoundCube[1].item:SetItem(soulSave.Id, 1, soulSave);
  compoundCube[1].item:SetEvent(function()
    UI.Open(UISoulInfo, soulSave);
  end);

  compoundCube[2].item:SetItem(usingItemInfo[2].itemId, 1);
  compoundCube[2].item:SetEvent();

  compoundCube[3].item:SetItem(usingItemInfo[3].itemId, 1);
  compoundCube[3].item:SetEvent();
end

function UISoul.SetButtonsAndHint()
  buttons[2].gameObject:SetActive(currentTag ~= ESoulTag.Breakthrough and currentTag ~= ESoulTag.SkillLv);

  if currentTag == ESoulTag.Compound then
    buttons[1].text_Hint.text = string.Get(21027);
    buttons[2].text_Hint.text = string.Get(10619);
    buttons[3].text_Hint.text = string.Get(20991);

    for i = 1, 3 do
      compoundCube[i].text_Hint.text = string.Concat(string.Get(23679), i);
    end

  elseif currentTag == ESoulTag.Intensify then
    buttons[1].text_Hint.text = string.Get(10409);
    buttons[2].text_Hint.text = string.Get(23698);
    buttons[3].text_Hint.text = string.Get(20991);

    compoundCube[1].text_Hint.text = string.Get(23679);
    compoundCube[2].text_Hint.text = string.Get(23681);
    compoundCube[3].text_Hint.text = string.Get(23682);
  elseif currentTag == ESoulTag.Breakthrough then
    buttons[1].text_Hint.text = string.Get(10601);
    buttons[3].text_Hint.text = string.Get(20991);

    compoundCube[1].text_Hint.text = string.Get(23679);
    compoundCube[3].text_Hint.text = string.Get(23691);
  elseif currentTag == ESoulTag.SkillLv then
    buttons[1].text_Hint.text = string.Get(20461);
    buttons[3].text_Hint.text = string.Get(20991);

    compoundCube[1].text_Hint.text = string.Get(23679);
    compoundCube[3].text_Hint.text = string.Get(23692);
  elseif currentTag == ESoulTag.Affination then
    buttons[1].text_Hint.text = string.Get(23048);
    buttons[2].text_Hint.text = string.Get(23052);
    buttons[3].text_Hint.text = string.Get(20991);

    compoundCube[1].text_Hint.text = string.Get(23679);
    compoundCube[2].text_Hint.text = string.Get(23798);
    compoundCube[3].text_Hint.text = string.Get(23682);

    buttons[2].gameObject:SetActive(hasResult);

    if hasResult then
      buttons[1].text_Hint.text = string.Get(23125);
      buttons[3].text_Hint.text = string.Get(23054);
    end
  end
end

function UISoul.ShowRoleEquip()
  local equipSoul = Soul.GetEquippedSoul(currentRole.npcId);
  if equipSoul ~= nil and itemDatas[equipSoul.Id] ~= nil then
    image_equip:SetItem(equipSoul.Id, 1, equipSoul);
    image_equip:SetEvent(function()
      UI.Open(UISoulInfo, equipSoul, ESoulAction.Takeoff, function()
        if Item.CheckSoulBag() then
          ShowCenterMessage(string.Get(23708));
          return;
        end

        currentChosenSoul = equipSoul;

        this.SendEquip(2, currentChosenSoul);
      end);
    end);
  else
    image_equip:SetItem(0);
    image_equip:SetEvent();
  end
end

function UISoul.StartMixEffect(effectKind, soulData)
  this.StopMixEffect();

  local effectName = nil;
  local effectPage = 0;
  local effectSound = nil;
  local effectTime = 0;
  local loopTimes = -1;

  if effectKind == ESoulEffect.Compound then
    effectName = "Mixed";
    effectPage = 6;
    effectTime = 1.5;
    loopTimes = -1;
  elseif effectKind == ESoulEffect.Success then
    effectName = "UP";
    effectPage = 9;
    effectSound = "AudioClip_WA0035";
    effectTime = 2;
    loopTimes = 2;
  end
  
  if effectName == nil then return end
  if effectPage <= 0 then return end

  isPlayingAnime = true;
  TextureManager.SetPng(ETextureUseType.UI, effectName, rawImage_Mixed);
  rawImage_Mixed.rectTransform.sizeDelta = Vector2.New(rawImage_Mixed.texture.width, rawImage_Mixed.texture.height / effectPage);
  rawImage_Mixed:ShiftUV(1, effectPage, 0.1, loopTimes);
  rawImage_Mixed.enabled = true;

  if effectSound ~= nil then
    AudioManager.PlayOnce(effectSound, 0);
  end

    CGTimer.DoFunctionDelay(effectTime, function()
      if currentTag == ESoulTag.Compound then
        gameObject_Result.item:SetItem(soulData.Id, 1, soulData);
        gameObject_Result.item:SetEvent(function()
          UI.Open(UISoulInfo, soulData);
        end
        );

        if itemDatas[soulData.Id] ~= nil then
          ShowCenterMessage(string.format(string.Get(23684), itemDatas[soulData.Id].name))
          Chat.AddMessage(EChannel.System, string.format(string.Get(21179), itemDatas[soulData.Id]:GetName(true), 1));
        else
          logError("物品"..soulData.Id.."不存在")
        end

        if UI.IsVisible(UISoul) then
          currentChosenSoul = nil;
          image_questionMark.enabled = false;
          hasResult = true;
          this.UpdateUI();
        end
      end

      isPlayingAnime = false;
    end
    );
end

function UISoul.StopMixEffect()
  rawImage_Mixed.texture = nil;
  rawImage_Mixed:StopShiftUV();
  rawImage_Mixed.enabled = false;
  isPlayingAnime = false;
end

  --region onclick
function UISoul.OnClick_SwitchRole(uiEvent)
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

function UISoul.OnClick_Detail()
  if currentRole == nil then return end;
  showDetail = not showDetail;
  this.UpdateDetail();
end

function UISoul.UpdateDetail()
  if currentTag ~= ESoulTag.Equip then return end

  gameObject_Detail:SetActive(showDetail);
  gameObject_DetailRoot:ShowInfo(currentRole);
end

function UISoul.OnClick_Button(uiEvent)
  local num = uiEvent.parameter;

  if currentTag == ESoulTag.Compound then
    if isPlayingAnime then return end
    if num == 1 then
      if table.Count(usingItemInfo) == 3 then
        ShowCenterMessage(string.Get(21131));  --合成欄位已滿
        return;
      end

      hasResult = false;
      UIBag.SkidBlink();

      local index = 1;
      if usingItemInfo[1] ~= nil then
        index = 2;
      end

      local bag = Item.GetBagByCategory(EThingsCategory.All, EThings.Soul);
      local count = table.Count(bag);
      local qualityCount = 0;
      local start = 1;
      local bagData = bag[start];

      for i = 1, count do
        if bag[i] ~= nil and bag[i].quality == bagData.quality and not bag[i].isLock then
          qualityCount = qualityCount + 1;
        end

        if qualityCount >= 3 then
          break;
        else
          if bagData.quality ~= bag[i].quality and not bag[i].isLock then
            start = i;
            bagData = bag[start];
            qualityCount = 1;
          end
        end
      end

      if qualityCount < 3 then
        ShowCenterMessage(string.Get(23875));
      else
        for i = start, count do
          local bagData = bag[i];

          if bagData ~= nil and not bagData.isLock then
            if index > 3 then break end
            if index == 1 then
              this.SetSoulDataToUsing(bagData.bagIndex, index, bagData);
              index = index + 1;
            else
              if bagData.quality == usingItemInfo[1].quality and usingItemInfo[1].soulData.sn ~= bagData.sn then
                this.SetSoulDataToUsing(bagData.bagIndex, index, bagData);
                index = index + 1;
              end
            end
          end
        end
      end
    elseif num == 2 then
      if table.Count(usingItemInfo) ~= 3 then return end

      local hasIntBreak = false;
      for i = 1, 3 do
        if usingItemInfo[i] == nil then return end
        if Soul.HasBreakInt(usingItemInfo[i]) then
          hasIntBreak = true;
          break;
        end
      end

      if hasIntBreak then
        ShowCheckMessage(function(result)
            if result then
              this.SendCompound();
            end
          end,
          string.Get(23697)
        );
      else
        this.SendCompound();
      end
    elseif num == 3 then
      isInUse = false;
      currentChosenSoul = nil;
      hasResult = false;
      this.ClearData();
    end
  elseif currentTag == ESoulTag.Intensify then
    if num == 1 then
      if usingItemInfo[1] == nil then
        if hasResult then
          ShowCenterMessage(string.Get(23699));
        end
        return;
      end

      local soulData = soulLevelDatas[usingItemInfo[1].soulData.lv];

      if usingItemInfo[2].quant < soulData:GetLevelData(usingItemInfo[1].quality).reel then
        ShowCenterMessage(string.Get(23685));
        return;
      end

      if Role.GetPlayerCurrency(ECurrency.TS) < usingItemInfo[3].quant then
        ShowCenterMessage(string.Get(23686));
        return;
      end

      reelOver = false;
      coinOver = false;
      intCount = 1;

      this.SendIntensify(intCount);
    elseif num == 2 then
      if usingItemInfo[1] == nil then
        if hasResult then
          ShowCenterMessage(string.Get(23699));
        end
        return;
      end

      local temp = usingItemInfo[1].soulData;
      local soulData = soulLevelDatas[temp.lv];
      if usingItemInfo[2].quant < soulData:GetLevelData(temp.quality).reel then
        ShowCenterMessage(string.Get(23685));
        return;
      end

      if Role.GetPlayerCurrency(ECurrency.TS) < usingItemInfo[3].quant then
        ShowCenterMessage(string.Get(23686));
        return;
      end

      intCount = 0;
      local lv = temp.lv;
      local totalReel = 0;
      local totalCoin = 0;

      for i = 1, 10 do
        if lv == temp:GetLimitLv() then break end
        totalReel = totalReel + soulLevelDatas[lv]:GetLevelData(temp.quality).reel;
        totalCoin = totalCoin + soulLevelDatas[lv]:GetLevelData(temp.quality).coin;
        if (totalReel <= Item.GetItemCount(29801) and totalCoin <= Role.GetPlayerCurrency(ECurrency.TS)) then
          intCount = intCount + 1;
        end

        lv = lv + 1;
      end

      reelOver = totalReel > Item.GetItemCount(29801);
      coinOver = totalCoin > Role.GetPlayerCurrency(ECurrency.TS);

      ShowCheckMessage(function(result)
        if result then
          this.SendIntensify(intCount);
        end
      end,
        string.format(string.Get(23700), totalReel, totalCoin)
      );
    elseif num == 3 then
      this.ClearData();
      hasResult = false;
      reelOver = false;
      coinOver = false;
    end
  elseif currentTag == ESoulTag.Breakthrough then
    if num == 1 then
      if usingItemInfo[1] == nil or usingItemInfo[3] == nil then return end

      if Soul.HasBreakInt(usingItemInfo[3]) then
        ShowCheckMessage(function(result)
          if result then
            this.SendBreakthroughOrSkillLv();
          end
        end,
          string.Get(23697)
        );
      else
        this.SendBreakthroughOrSkillLv();
      end
    elseif num == 3 then
      this.ClearData();
      hasResult = false;
      currentChosenSoul = nil;
    end
  elseif currentTag == ESoulTag.SkillLv then
    if num == 1 then
      if usingItemInfo[1] == nil or usingItemInfo[3] == nil then return end

      local total = 0;
      local count = 0;

      for i = 1, table.Count(usingItemInfo[1].soulData.skill) do
        if not usingItemInfo[1].show[i] then
          local id = usingItemInfo[1].soulData.skill[i].id;
          if id ~= 0 then
            total = total + 1;
            if usingItemInfo[1].soulData:SkillIsMax(id) then
              count = count + 1;
            end
          end
        end
      end

      if total == count then
        ShowCenterMessage(string.Get(23694));
        return;
      end

      if Soul.HasBreakInt(usingItemInfo[3]) then
        ShowCheckMessage(function(result)
          if result then
            this.SendBreakthroughOrSkillLv();
          end
        end,
          string.Get(23697)
        );
      else
        this.SendBreakthroughOrSkillLv();
      end
    elseif num == 3 then
      this.ClearData();
      hasResult = false;
      currentChosenSoul = nil;
    end
  elseif currentTag == ESoulTag.Affination then
    if num == 1 then
      if usingItemInfo[1] == nil then return end

      if usingItemInfo[2].quant < 1 then
        ShowCenterMessage(string.Get(23685));
        return;
      end

      if Role.GetPlayerCurrency(ECurrency.TS) < usingItemInfo[3].quant then
        ShowCenterMessage(string.Get(23686));
        return;
      end

      this.SendAffination(1);

    elseif num == 2 then
      this.SendAffination(3);
    elseif num == 3 then
      if not hasResult then
        this.ClearData();
        currentChosenSoul = nil;
        this.UpdateUI();
      else
        this.SendAffination(2);
      end
    end
  end

  this.UpdateUI();
end

function UISoul.OnClick_Skill(uiEvent)
  local equipSoul = Soul.GetEquippedSoul(currentRole.npcId);
  UI.Open(UISkillHint, uiEvent.parameter.skillId, equipSoul, true)
end
  --endregion

  --region open bag info
function UISoul.OpenBagEquip()
  if not Soul.CheckItemCon(currentChosenSoul, true) then return end
  if currentRole == nil then return end
  if currentRole:GetAttribute(EAttribute.Element) ~= currentChosenSoul.element then
    ShowCenterMessage(string.Get(40342));
    return;
  end

  local equipSoul = Soul.GetEquippedSoul(currentRole.npcId);

  if equipSoul ~= nil then
    if Item.CheckSoulBag() then
      ShowCenterMessage(string.Get(23708));
      return;
    end

    this.SendEquip(2, equipSoul);
    return;
  end

  this.SendEquip(1, currentChosenSoul);
end

function UISoul.OpenBagCompound(bagIndex)
  local itemSave = Item.GetBagItem(EThings.Soul, bagIndex);
  if not Soul.CheckItemCon(itemSave, true, true) then return end

  local emptyIndex = 0;
  for i = 1, 3 do
    if usingItemInfo[i] == nil then
      emptyIndex = i;
      break;
    end
  end

  if emptyIndex == 0 then
    ShowCenterMessage(string.Get(21131));  --合成欄位已滿
    return;
  end

  if emptyIndex == 1 then
    isInUse = true;
    hasResult = false;
  else
    if usingItemInfo[1].quality ~= currentChosenSoul.quality then
      ShowCenterMessage(string.Get(23683))
      return;
    end
  end

  UI.Close(UISoulInfo);
  this.SetSoulDataToUsing(bagIndex, emptyIndex, itemSave);
  this.UpdateUI();
end

function UISoul.OpenBagIntensify(bagIndex)
  local itemSave = Item.GetBagItem(EThings.Soul, bagIndex);
  if not Soul.CheckItemCon(itemSave, true) then return end

  if usingItemInfo[1] ~= nil then
    this.ClearData();
  end

  UI.Close(UISoulInfo);
  this.SetSoulDataToUsing(bagIndex, 1, itemSave)
  this.SetIntAffUsing(itemSave);
  hasResult = false;
  this.UpdateUI();
end

function UISoul.OpenBagBreakthrough(bagIndex)
  local itemSave = Item.GetBagItem(EThings.Soul, bagIndex);
  if not Soul.CheckItemCon(itemSave, true) then return end

  if usingItemInfo[1] == nil then
    this.SetSoulDataToUsing(bagIndex, 1, itemSave);
    hasResult = false;
    UIBag.UpdateUI(false);
  else
    if not Soul.CheckItemCon(itemSave, false, true) then return end

    if itemSave.starLv > usingItemInfo[1].soulData.starLv then
      ShowCenterMessage(string.Get(23695));
    end

    if usingItemInfo[3] == nil then
      this.SetSoulDataToUsing(bagIndex, 3, itemSave);
    else
      ShowCenterMessage(string.Get(21131));
      return;
    end
  end

  UI.Close(UISoulInfo);
  this.UpdateUI();
end

function UISoul.OpenBagSkillLv(bagIndex)
  local itemSave = Item.GetBagItem(EThings.Soul, bagIndex);
  if not Soul.CheckItemCon(itemSave, true) then return end

  if usingItemInfo[1] == nil then
    this.SetSoulDataToUsing(bagIndex, 1, itemSave);
    hasResult = false;
    UIBag.UpdateUI(false);
  else
    if not Soul.CheckItemCon(itemSave, false, true) then return end

    if usingItemInfo[3] == nil then
      this.SetSoulDataToUsing(bagIndex, 3, itemSave);
    else
      return;
    end
  end

  UI.Close(UISoulInfo);
  this.UpdateUI();
end

function UISoul.OpenBagAffination(bagIndex)
  local itemSave = Item.GetBagItem(EThings.Soul, bagIndex);
  if not Soul.CheckItemCon(itemSave, true) then return end

  if usingItemInfo[1] == nil then
    UI.Close(UISoulInfo);
    UIBag.SkidBlink();
    this.SetSoulDataToUsing(bagIndex, 1, itemSave)
    this.SetIntAffUsing(itemSave);
    hasResult = false;
    this.UpdateUI();
  else
    ShowCenterMessage(string.Get(23795));
    return;
  end
end
  --endregion
