Cloud = {};
Cloud.__index = Cloud;

local tempVec = Vector3.New(0, 0, 0);
local creatTickCount = 0;
local root = nil;

function Cloud.New(i)
  local self = {};
  setmetatable(self, Cloud);
  
  if i % 2 == 0 then
    self.showX = Scene.cameraPosition.x - Scene.currentSceneHalfWidth + math.random(Scene.currentSceneHalfWidth * 2);
    self.showY = Scene.cameraPosition.y - Scene.currentSceneHalfHeight - 150;
  else
    self.showX = Scene.cameraPosition.x - Scene.currentSceneHalfWidth - 150;
    self.showY = Scene.cameraPosition.y - Scene.currentSceneHalfHeight + math.random(Scene.currentSceneHalfHeight * 2);
  end

  local r = math.random(2)
  if r == 1 then
    self.speed = 0.01 * 1000 * 5 ;
  elseif r == 2 then
    self.speed = 0.015 * 1000 * 5;
  else
    self.speed = 0.015 * 1000 * 5;
  end

  self.ObjX = self.showX + Scene.currentSceneHalfWidth * 2; --目地的
  self.ObjY = self.showY + Scene.currentSceneHalfHeight * 2; --目地的

  self.arrive = false;
  self.arriveTickCount = 0;

  if root == nil then
    root = UIShowSceneEffect.GetUIObject()
  end

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(root.transform);
  self.gameObject.name = string.format("Cloud_%d", i);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");

  tempVec.x = (self.showX - Scene.cameraPosition.x) * UI.scale * Scene.cameraScale;
  tempVec.y = (Scene.cameraPosition.y - self.showY) * UI.scale * Scene.cameraScale;
  self.rectTransform.anchoredPosition = tempVec;
  self.rectTransform.localScale = Vector3.one;

  self.rawImage = self.gameObject:GetComponent("UIRawImage");

  self.material = poolMgr:Get("LightMaterial");
  self.material.shader = UnityEngine.Shader.Find("Custom/LightShader");
  self.rawImage.material = self.material;
  self.material:SetFloat("_InvisibleAlpha", 8 / EffectLight.MAX_ALPHA); 

  TextureManager.SetPngByCallBack(ETextureUseType.Scene, string.Concat("Cloud", math.random(5)), self.rawImage,
    function(sucess)
      self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height);
    end
  );
  self.rawImage:StopShiftUV();
  self.rawImage:ClearUV();

  return self;
end

function Cloud:GetPosition()
  SceneEffect.GetPosition(self);
end

local cloud = {};

function Cloud.Update()
    creatTickCount = creatTickCount + CGTimer.deltaTime;
    if creatTickCount >= SceneEffect.CloudCreateTime then
      creatTickCount = 0;
      for i = 1, SceneEffect.MaxCloud do
        if cloud[i] == nil then              
          cloud[i] = Cloud.New(i);
          break;
        end
      end
    end

    for i = 1, SceneEffect.MaxCloud do
      if cloud[i] ~= nil then
        cloud[i]:GetPosition();
        if cloud[i].arrive then
          cloud[i].arriveTickCount = cloud[i].arriveTickCount + CGTimer.deltaTime;
          if cloud[i].arriveTickCount >= SceneEffect.CloudArriveTime then
            poolMgr:Release("LightMaterial", cloud[i].material);
            cloud[i].rawImage.material = nil; 
            poolMgr:Release("BaseSceneEffect", cloud[i].gameObject);
            cloud[i] = nil;
          end
        end
      end
    end
end

function Cloud.Stop()
  CGTimer.RemoveListener(Cloud.Update);

  for i = 1, SceneEffect.MaxCloud do
    if cloud[i] ~= nil then
      poolMgr:Release("BaseSceneEffect", cloud[i].gameObject);
      cloud[i] = nil;
    end
  end
end
