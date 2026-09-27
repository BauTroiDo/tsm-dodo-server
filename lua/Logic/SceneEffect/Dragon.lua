Dragon = {};
Dragon.__index = Dragon;

local tempVec = Vector3.New(0, 0, 0);
local creatTickCount = 0;
local root = nil;

local currentDragonCount = 0;

function Dragon.New(i)
  local self = {};
  setmetatable(self, Dragon);

  local speed = math.random(3) * 0.1;
  local showX = math.random(MapManager.mapWidth);
  local showY = math.random(MapManager.mapHeight);

  local targetX;

  local fileName;
  local r = math.random(4);
  if r == 1 then
    fileName = SceneEffect.DragonName1;
    targetX = MapManager.mapWidth;
  elseif r == 2 then
    fileName = SceneEffect.DragonName2;
    targetX = 0;
  elseif r == 3 then
    fileName = SceneEffect.DragonName3;
    targetX = MapManager.mapWidth;
  elseif r == 4 then
    fileName = SceneEffect.DragonName4;
    targetX = 0;
  end

  self.dragon = EffectLight.New(fileName, 300, 1, 4, 4, EEffectLightTracer.Line, showX, showY, 255, true, 0, targetX, showY, speed, nil, true);
  self.dragon.endCallback = Dragon.ArriveTarget;
  self.dragon.index = i;

  return self;
end

local dragon = {};

function Dragon.Update()
  if SceneEffect.MaxDragon > currentDragonCount then
    for i = 1, SceneEffect.MaxDragon do
      if dragon[i] == nil then
        dragon[i] = Dragon.New(i);
        currentDragonCount = currentDragonCount + 1; 
      end
    end
  end
end

function Dragon.ArriveTarget(args)
  currentDragonCount = currentDragonCount - 1;
  dragon[args.index] = nil;
end

function Dragon.Stop()
  CGTimer.RemoveListener(Dragon.Update);

  for i = 1, SceneEffect.MaxDragon do
    if dragon[i] ~= nil then
      dragon[i].dragon:Free();
      dragon[i] = nil;
    end
  end

  currentDragonCount = 0;
end
