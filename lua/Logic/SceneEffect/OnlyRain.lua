OnlyRain = {};
OnlyRain.__index = OnlyRain;

local tempVec = Vector3.New(0, 0, 0);
local tickCount = 0;
local root = nil;

function OnlyRain.New(i)
  local self = {};
  setmetatable(self, OnlyRain);

--  math.randomseed(CGTimer.time);

  local r = math.random(3)
  if r == 1 then
    self.speed = 0.3 * 1000 * 5;
  elseif r == 2 then
    self.speed = 0.4 * 1000 * 5;
  elseif r == 3 then
    self.speed = 0.5 * 1000 * 5;
  else
    self.speed = 0.4 * 1000 * 5;
  end

  self.showX = Scene.cameraPosition.x + math.random(Scene.currentSceneHalfWidth * 2) - Scene.currentSceneHalfWidth; --Ground.posx+random(1200);
  self.showY = Scene.cameraPosition.y + 0 - Scene.currentSceneHalfHeight;
  self.ObjY = Scene.cameraPosition.y + ( math.random(Scene.currentSceneHalfHeight * 2 * 0.75) + Scene.currentSceneHalfHeight * 0.25 ) - Scene.currentSceneHalfHeight; --ObjY:=Ground.posy+random(450)+150;
  self.ObjX = self.showX - 50;

  self.deltaX = 0;
  self.arrive = false;
  self.arriveTickCount = 0;

  if root == nil then
    root = UIShowSceneEffect.GetUIObject();
  end

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(root.transform);
  self.gameObject.name = string.format("OnlyRain_%d", i);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");

  tempVec.x = (self.showX - Scene.cameraPosition.x) * UI.scale * Scene.cameraScale;
  tempVec.y = (Scene.cameraPosition.y - self.showY) * UI.scale * Scene.cameraScale;
  self.rectTransform.anchoredPosition = tempVec;
  self.rectTransform.localScale = Vector3.one;

  self.rawImage = self.gameObject:GetComponent("UIRawImage");
  self.rawImage.material = ElementController.ElementMaterial;
  self.rawImage:ShiftUV(1, SceneEffect.OnlyRainFrameCount, 150 * 0.001, -1);
  
  if math.random(100) > 80 then   --約十分之二出現種類二雨
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.OnlyRainPicName2, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.OnlyRainFrameCount);
      end
    );  
  else
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.OnlyRainPicName1, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.OnlyRainFrameCount);
      end
    );  
  end

  return self;
end

function OnlyRain:GetPosition()
  SceneEffect.GetPosition(self);
end

local onlyRain = {};

function OnlyRain.Update()
    tickCount = tickCount + CGTimer.deltaTime;
    if tickCount >= SceneEffect.RainCreateTime then
      tickCount = 0;
      for i = 1, SceneEffect.FT_MaxRain do
        if onlyRain[i] == nil then              
          onlyRain[i] = OnlyRain.New(i);
          break;
        end
      end
    end

    for i = 1, SceneEffect.FT_MaxRain do
      if onlyRain[i] ~= nil then
        onlyRain[i]:GetPosition();
        if onlyRain[i].arrive then
          onlyRain[i].arriveTickCount = onlyRain[i].arriveTickCount + CGTimer.deltaTime;
          if onlyRain[i].arriveTickCount > SceneEffect.OnlyRainArriveTime then
            poolMgr:Release("BaseSceneEffect", onlyRain[i].gameObject);
            onlyRain[i] = nil;
          end
        end
      end
    end
end

function OnlyRain.Stop()
  CGTimer.RemoveListener(OnlyRain.Update);

  for i = 1, SceneEffect.FT_MaxRain do
    if onlyRain[i] ~= nil then
      poolMgr:Release("BaseSceneEffect", onlyRain[i].gameObject);
      onlyRain[i] = nil;
    end
  end
end
