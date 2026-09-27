AnimationController = {}
AnimationController.__index = AnimationController;

local this = AnimationController;

this.animations = {};

function AnimationController.Play(animationId, transform, callBack, loadBack, dependenciesResourceName, parameter)
  if dependenciesResourceName == nil then
    dependenciesResourceName = "";
  end

  if this.animations[animationId] == nil then
    this.animations[animationId] = AnimationController.New(animationId, transform, callBack, loadBack, dependenciesResourceName, parameter);
  end
end

function AnimationController.New(animationId, transform, callBack, loadBack, dependenciesResourceName, parameter)
  local self = {};
  setmetatable(self, AnimationController);

  CGResourceManager.Load(
    string.format( "Animation_%05d", animationId),
    dependenciesResourceName,
    function(assetName, asset)
      self.object = GameObject.Instantiate(asset);
      self.object.name = assetName;
      self.object.transform:SetParent(transform);
      self.object.transform.localPosition = Vector3.zero;
      self.object.transform.localScale = Vector3.one;
      self.object.transform.localRotation = Quaternion.identity;
      local rectTrans =  self.object:GetComponent("RectTransform");
      if rectTrans ~= nil then 
        rectTrans:ForceUpdateRectTransforms();
      end

      self.dontDestory = false;

      self.animationId = animationId;

      self.uiAnimationEvent = self.object:GetComponent("UIAnimationEvent");
      self.uiAnimationEvent:SetAnimateCallBck(AnimationController.AnimateCallBck);
      self.uiAnimationEvent.parameter = animationId;

      self.callBack = callBack;
      self.parameter = parameter;

      if loadBack ~= nil then 
        loadBack(self);
      end
    end
  );

  return self;
end

function AnimationController:SetDontDestory()
   self.dontDestory = true;
end

function AnimationController.AnimateCallBck(animationEvent)
  local animationId = animationEvent.parameter;

  if this.animations[animationId].callBack ~= nil then
    this.animations[animationId].callBack();
  end

  if not this.animations[animationId].dontDestory then
    this.Destory(animationId);
  end
end

function AnimationController.Destory(animationId)
  if this.animations[animationId] ~= nil and this.animations[animationId].object ~= nil then
    destroy(this.animations[animationId].object);

    this.animations[animationId] = nil;
  end
end