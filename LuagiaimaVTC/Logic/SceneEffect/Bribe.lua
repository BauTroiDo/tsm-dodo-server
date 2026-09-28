Bribe = {};
Bribe.__index = Bribe;

local tempVec = Vector3.New(0, 0, 0);
local creatTickCount = 0;
local root = nil;

function Bribe.New(i, gdThing)
  local self = {};
  setmetatable(self, Bribe);

--  math.randomseed(CGTimer.time); 

  local r = math.random(3)
  if r == 1 then
    self.speed = 0.012 * 1000 * 5 ;
  elseif r == 2 then
    self.speed = 0.015 * 1000 * 5;
  elseif r == 3 then
    self.speed = 0.018 * 1000 * 5;
  else
    self.speed = 0.015 * 1000 * 5;
  end

  self.showX = Scene.cameraPosition.x + math.random(Scene.currentSceneHalfWidth * 2) - Scene.currentSceneHalfWidth;
  if gdThing.position.y > Scene.currentSceneHalfHeight * 4 then
    self.showY = gdThing.position.y - Scene.currentSceneHalfHeight * 4;
  elseif gdThing.position.y > Scene.currentSceneHalfHeight * 2 then
    self.showY = gdThing.position.y - Scene.currentSceneHalfHeight * 2;
  else
    self.showY = 0;
  end

  self.ObjX = gdThing.position.x;
  self.ObjY = gdThing.position.y;

  self.deltaX = math.random(60) + 5;
  self.arrive = false;
  self.arriveTickCount = 0;

  if root == nil then
    root = UIShowSceneEffect.GetUIObject()
  end

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(root.transform);
  self.gameObject.name = string.format("Bribe_%d", i);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  
  tempVec.x = (self.showX - Scene.cameraPosition.x) * UI.scale * Scene.cameraScale;
  tempVec.y = (Scene.cameraPosition.y - self.showY) * UI.scale * Scene.cameraScale;
  self.rectTransform.anchoredPosition = tempVec;
  self.rectTransform.localScale = Vector3.one;

  self.rawImage = self.gameObject:GetComponent("UIRawImage");
  self.rawImage.material = ElementController.ElementMaterial;
  self.rawImage:ShiftUV(1, SceneEffect.BribeFrameCount, (math.random(3) * 30 + 250) * 0.001, -1);

  if gdThing.value == 46334 or gdThing.value == 46337 then
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.BribePicName3, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.BribeFrameCount);
      end
    );
  else
    if math.random(10) > 7 then   --約十分之三出現種類二雨
      TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.BribePicName2, self.rawImage,
        function(sucess)
          self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.BribeFrameCount);
        end
      );
    else
      TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.BribePicName1, self.rawImage,
        function(sucess)
          self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.BribeFrameCount);
        end
      );
    end
  end

  self.gdThing = gdThing;

  return self;
end

function Bribe:GetPosition()
  SceneEffect.GetPosition(self);
end

local bribe = {};

function Bribe.Start()
  CGTimer.AddListener(Bribe.Update, 0, false);
end

function Bribe.Create(gdThing)
  for i = 1, SceneEffect.FT_MaxBribe do
    if bribe[i] == nil then              
      bribe[i] = Bribe.New(i, gdThing);
      break;
    end
  end

  Bribe.Start();
end

function Bribe.Update()
  for i = 1, SceneEffect.FT_MaxBribe do
    if bribe[i] ~= nil then
      bribe[i]:GetPosition();
      if bribe[i].arrive then
        bribe[i].arriveTickCount = bribe[i].arriveTickCount + CGTimer.deltaTime;
        if bribe[i].arriveTickCount >= SceneEffect.BribeArriveTime then
          bribe[i].gdThing:UpdateView();
          poolMgr:Release("BaseSceneEffect", bribe[i].gameObject);
          bribe[i] = nil;
        end
      end
    end
  end
end

function Bribe.Stop()
  CGTimer.RemoveListener(Bribe.Update);

  for i = 1, SceneEffect.FT_MaxBribe do
    if bribe[i] ~= nil then
      poolMgr:Release("BaseSceneEffect", bribe[i].gameObject);
      bribe[i] = nil;
    end
  end
end