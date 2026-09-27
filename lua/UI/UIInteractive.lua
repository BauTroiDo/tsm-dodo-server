UIInteractive = {}
local this = UIInteractive;

this.name = "UIInteractive";
this.uiController = nil;

local rectTransform_Function;
local sizeFitter_Function;

local text_Name;

local image_Interactive = {};

local dirty = 0;

function UIInteractive.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;

  local tempEvent;

  rectTransform_Function =  uiController:FindGameObject("Image_Line"):GetComponent("RectTransform");
  sizeFitter_Function = rectTransform_Function:GetComponent("SizeFitter");

  text_Name = uiController:FindText("Text_Name"):GetComponent("Text");

  for i = 1, table.Count(EInteractive) do
    image_Interactive[i] = uiController:FindImage(string.Concat("Image_Interactive (", i, ")"));

    tempEvent = uiController:FindEvent(string.Concat("Image_Interactive (", i, ")"));
    if tempEvent ~= nil then
      tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Interactive);
      tempEvent.parameter = i;
    end
  end

  --手遊版 prefab 移除了 Image_Interactive (10)(11)，動態克隆
  for _, dst in ipairs({10, 11}) do
    if image_Interactive[dst] == nil then
      --找到第一個存在的按鈕作為模板
      local template = nil
      for i = 1, table.Count(EInteractive) do
        if image_Interactive[i] ~= nil then
          template = image_Interactive[i]
          break
        end
      end
      if template ~= nil then
        local clone = newObject(template.gameObject)
        clone.transform:SetParent(template.gameObject.transform.parent)
        clone:GetComponent("RectTransform").localScale = Vector3.one
        clone.name = string.Concat("Image_Interactive (", dst, ")")

        image_Interactive[dst] = clone:GetComponent("Image")

        local cloneEvent = clone:GetComponent("UIEvent")
        if cloneEvent ~= nil then
          cloneEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Interactive)
          cloneEvent.parameter = dst
        end
      end
    end
  end

  tempEvent = uiController:FindEvent("Image_SearchTarget");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SearchTarget);
end

function UIInteractive.OnOpen()
  this.UpdateTarget();

  return true;
end

function UIInteractive.OnClose()
end

function UIInteractive.Update()
  if dirty <= 0 then return end
  dirty = dirty - 1;

  sizeFitter_Function:SetDirty();
end

function UIInteractive.ShowFunction()
  LuaHelper.DoTweenDOScale(rectTransform_Function, 1, 0.1);
end

function UIInteractive.HideFunction()
  LuaHelper.DoTweenDOScale(rectTransform_Function, 0, 0.1);
end

function UIInteractive.UpdateTarget()
  if Role.target ~= nil then
    for i = 1, table.Count(EInteractive) do
      if image_Interactive[i] ~= nil then
        local active, color = Role.target:CheckInteractive(i);

        image_Interactive[i].gameObject:SetActive(active);

        if active then
          LuaHelper.SetColor(image_Interactive[i], color);
        end
      end
    end
    dirty = 2;

    text_Name.text = Role.target.name;

    this.ShowFunction();
  else
    this.HideFunction();
  end
end

function UIInteractive.OnClick_Interactive(uiEvent)
  if Role.target == nil then return end
  Role.target:OnInteractive(uiEvent.parameter);
end

function UIInteractive.OnClick_SearchTarget()
  TargetManager.ChangeTarget(nil, BattleRoyale.GetBattleRoyaleState());
  if BattleRoyale.GetBattleRoyaleState() then
    BattleRoyale.CancelSkill();
    UIInteractive.UpdateTarget();
  end
end