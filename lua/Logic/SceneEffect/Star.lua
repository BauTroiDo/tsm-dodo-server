Star = {};
Star.__index = Star;

local tempVec = Vector3.New(0, 0, 0);
local tickCount = 0;
local root = nil;

function Star.New(i)
  local self = {};
  setmetatable(self, Star);

--  math.randomseed(CGTimer.time);

  local r = math.random(3)
  if r == 1 then
    self.speed = 0.1 * 1000 * 5;
  elseif r == 2 then
    self.speed = 0.15 * 1000 * 5;
  elseif r == 3 then
    self.speed = 0.2 * 1000 * 5;
  else
    self.speed = 0.15 * 1000 * 5;
  end

  self.showX = Scene.cameraPosition.x + math.random(Scene.currentSceneHalfWidth * 3) - Scene.currentSceneHalfWidth; --Ground.posx+random(1200);
  self.showY = Scene.cameraPosition.y + 0 - Scene.currentSceneHalfHeight;
  self.ObjY = Scene.cameraPosition.y + Scene.currentSceneHalfHeight * 2 - Scene.currentSceneHalfHeight;
  self.ObjX = self.showX - 300;
  self.deltaX = 0;
  self.arrive = false;
  self.arriveTickCount = 0;

  if root == nil then
    root = UIShowSceneEffect.GetUIObject()
  end

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(root.transform);
  self.gameObject.name = string.format("Star_%d", i);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");

  tempVec.x = (self.showX - Scene.cameraPosition.x) * UI.scale * Scene.cameraScale;
  tempVec.y = (Scene.cameraPosition.y - self.showY) * UI.scale * Scene.cameraScale;
  self.rectTransform.anchoredPosition = tempVec;
  self.rectTransform.localScale = Vector3.one;

  self.rawImage = self.gameObject:GetComponent("UIRawImage");
  self.rawImage.material = ElementController.ElementMaterial;
  if math.random(100) > 70 then   --約十分之三出現種類二星星
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.StarPicName2, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.StarFrameCount);
      end
    );
  else
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.StarPicName1, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.StarFrameCount);
      end
    );
  end

  local interval = 50 * 0.001
  self.rawImage:ShiftUV(1, SceneEffect.StarFrameCount, interval, -1);

  return self;
end

function Star:GetPosition()
  SceneEffect.GetPosition(self);
end

local star = {};

function Star.Update()
    tickCount = tickCount + CGTimer.deltaTime;
    if tickCount >= SceneEffect.StarCreateTime then
      tickCount = 0;
      for i = 1, SceneEffect.FT_MaxStar do
        if star[i] == nil then              
          star[i] = Star.New(i);
          break;
        end
      end
    end

    for i = 1, SceneEffect.FT_MaxStar do
      if star[i] ~= nil then
        star[i]:GetPosition();
        if star[i].arrive then
          star[i].arriveTickCount = star[i].arriveTickCount + CGTimer.deltaTime;
          if star[i].arriveTickCount >= SceneEffect.StarArriveTime then
            poolMgr:Release("BaseSceneEffect", star[i].gameObject);
            star[i] = nil;
          end
        end
      end
    end
end

function Star.Stop()
  CGTimer.RemoveListener(Star.Update);

  for i = 1, SceneEffect.FT_MaxStar do
    if star[i] ~= nil then
      poolMgr:Release("BaseSceneEffect", star[i].gameObject);
      star[i] = nil;
    end
  end
end
