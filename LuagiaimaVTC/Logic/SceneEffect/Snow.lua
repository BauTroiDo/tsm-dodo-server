Snow = {};
Snow.__index = Snow;

local tempVec = Vector3.New(0, 0, 0);
local creatTickCount = 0;
local root = nil;

function Snow.New(i)
  local self = {};
  setmetatable(self, Snow);

--  math.randomseed(CGTimer.time);

  local r = math.random(3)
  if r == 1 then
    self.speed = 0.01 * 1000 * 5;
  elseif r == 2 then
    self.speed = 0.015 * 1000 * 5;
  elseif r == 3 then
    self.speed = 0.02 * 1000 * 5;
  else
    self.speed = 0.015 * 1000 * 5;
  end

  self.showX = Scene.cameraPosition.x + math.random(Scene.currentSceneHalfWidth * 2) - Scene.currentSceneHalfWidth;
  self.showY = Scene.cameraPosition.y + 0 - Scene.currentSceneHalfHeight;
  self.ObjY = Scene.cameraPosition.y + Scene.currentSceneHalfHeight * 2 - Scene.currentSceneHalfHeight ;

  r = math.random(2);
  if r == 1 then
    self.ObjX = self.showX+(math.random(5)*50);
  elseif r == 2 then
    self.ObjX = self.showX-(math.random(5)*50);
  end

  self.deltaX = math.random(20);
  self.arrive = false;
  self.arriveTickCount = 0;

  if root == nil then
    root = UIShowSceneEffect.GetUIObject()
  end

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(root.transform);
  self.gameObject.name = string.format("Snow_%d", i);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");

  tempVec.x = (self.showX - Scene.cameraPosition.x) * UI.scale * Scene.cameraScale;
  tempVec.y = (Scene.cameraPosition.y - self.showY) * UI.scale * Scene.cameraScale;
  self.rectTransform.anchoredPosition = tempVec;
  self.rectTransform.localScale = Vector3.one;

  self.rawImage = self.gameObject:GetComponent("UIRawImage");
  self.rawImage.material = ElementController.ElementMaterial;
  if math.random(100) > 90 then   --約十分之一出現種類二雪花
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.SnowPicName2, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.SnowFrameCount);
      end
    );  
  else
    TextureManager.SetPngByCallBack(ETextureUseType.Scene, SceneEffect.SnowPicName1, self.rawImage,
      function(sucess)
        self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.SnowFrameCount);
      end
    );  
  end

  local interval = ( math.random(3) * 100 + 200 ) * 0.001
  self.rawImage:ShiftUV(1, SceneEffect.SnowFrameCount, interval, -1);

  return self;
end

function Snow:GetPosition()
  SceneEffect.GetPosition(self);
end

local snow = {};

function Snow.Update()
    creatTickCount = creatTickCount + CGTimer.deltaTime;
    if creatTickCount >= SceneEffect.SnowCreateTime then
      creatTickCount = 0;
      for i = 1, SceneEffect.FT_MaxSnow do
        if snow[i] == nil then              
          snow[i] = Snow.New(i);
          break;
        end
      end
    end

    for i = 1, SceneEffect.FT_MaxSnow do
      if snow[i] ~= nil then
        snow[i]:GetPosition();
        if snow[i].arrive then
          snow[i].arriveTickCount = snow[i].arriveTickCount + CGTimer.deltaTime;
          if snow[i].arriveTickCount >= SceneEffect.SnowArriveTime then
            poolMgr:Release("BaseSceneEffect", snow[i].gameObject);
            snow[i] = nil;
          end
        end
      end
    end
end

function Snow.Stop()
  CGTimer.RemoveListener(Snow.Update);

  for i = 1, SceneEffect.FT_MaxSnow do
    if snow[i] ~= nil then
      poolMgr:Release("BaseSceneEffect", snow[i].gameObject);
      snow[i] = nil;
    end
  end
end
