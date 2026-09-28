Egg = {};
Egg.__index = Egg;

local eggNpc;
local eggItemId;
local awardCount;
local bornEggTime = 1.6;
local rollEggTime = 2;
local currentIndex = 1;

function Egg.New(name)
  local self = {};
  setmetatable(self, Egg);

  self.deltaX = 0;
  self.arrive = false;
  self.arriveTickCount = 0;

  self.gameObject = poolMgr:Get("BaseSceneEffect");
  self.gameObject.transform:SetParent(eggNpc.transform);

  self.gameObject.name = string.format("Egg_%s", name);
  self.rectTransform = self.gameObject:GetComponent("RectTransform");

  if name == SceneEffect.EggPicName then
    self.showX = -13;
    self.showY = 80;
  else
    self.showX = -48;
    self.showY = 20;
  end

  self.rectTransform.anchoredPosition = Vector3.New(self.showX, self.showY, 0);
  self.rectTransform.localScale = Vector3.one;
  self.rawImage = self.gameObject:GetComponent("UIRawImage");
  self.rawImage.raycastTarget = false;
  self.rawImage:ShiftUV(1, SceneEffect.EggFrameCount, 0.4, -1);

  TextureManager.SetPngByCallBack(ETextureUseType.Scene, name, self.rawImage,
    function(sucess)
      self.rectTransform.sizeDelta = Vector2.New(self.rawImage.texture.width, self.rawImage.texture.height / SceneEffect.EggFrameCount);
    end
  );

  return self;
end

function Egg:GetPosition()
  SceneEffect.GetPosition(self);
end

local egg = {};
local award = {};

--S:023-106 <轉蛋樂> Count(1)+<<NpcIdx(1)+EggKind(1)+aThingID(2)>>
function Egg.ReciveStartRollEgg(data)
  table.Clear(award);

  awardCount = data:ReadByte();
  for i = 1, awardCount do
    award[i] = {};
    award[i].npcId = data:ReadByte();
    award[i].kind = data:ReadByte();
    award[i].itemId = data:ReadUInt16();
  end

  Egg.StartRoll(award);
end

--[[
褐轉蛋   46131
藍轉蛋   46132
紅轉蛋   46133
綠轉蛋   46134
亮銀轉蛋 46135
黃金轉蛋 46136]]
function Egg.StartRoll(award)
  if award == nil then return end

  for k, v in pairs(award) do
    if award[k].npcId ~= nil then
      eggNpc = Role.GetRole(EHuman.MapNpc, award[k].npcId);
      if eggNpc == nil then return end
    end
  end

  Egg.Stop(false);

  egg[SceneEffect.EggPicName] = Egg.New(SceneEffect.EggPicName);
  CGTimer.AddListener(Egg.StopRoll, rollEggTime, false);
  currentIndex = 1;

  AudioManager.PlayOnce("WA0092", 0);
  AudioManager.PlayOnce("WA0093", 0);
end

function Egg.StopRoll()
  Egg.Clear();

  Egg.OpenEgg();

  AudioManager.PlayOnce("WA0093", 0);
end

function Egg.OpenEgg()
  CGTimer.RemoveListener(Egg.StopRoll);

  if award[currentIndex] ~= nil then
    egg[award[currentIndex].itemId] = Egg.New(award[currentIndex].itemId);
  end

  CGTimer.AddListener(Egg.Stop, (bornEggTime / awardCount) * currentIndex, false);
end

function Egg.Stop(sound)
  if sound ~= false then
    AudioManager.PlayOnce("WA0014", 0);
    ShowCenterMessage(string.Concat(string.Get(40049), itemDatas[award[currentIndex].itemId]:GetName(true)));

    CGTimer.RemoveListener(Egg.Stop);
    Egg.Clear();

    if not Egg.IsPlayOver() then
      currentIndex = currentIndex + 1;
      Egg.OpenEgg();
    end
  else
    --強制重新
    CGTimer.RemoveListener(Egg.Stop);
    Egg.Clear();
  end
end

function Egg.Clear()
  for k, v in pairs(egg) do
    poolMgr:Release("BaseSceneEffect", egg[k].gameObject);
    egg[k] = nil;
  end
end

function Egg.IsPlayOver()
  return currentIndex == awardCount;
end