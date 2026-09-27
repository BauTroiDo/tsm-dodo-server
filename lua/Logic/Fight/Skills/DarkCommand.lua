DarkCommand = InheritsFromBaseAttackSkill();

local FUZHOU_AMOUNT = 50;
local FUZHOU_PERIOD = 0.05;
local FUZHOU_RANDOM_RANGE = 200;
local fuZhouIndex = 0;

function DarkCommand:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  local skill = FightField.attack[self.humIdx];
  local enemy = DarkCommand.GetEnemy(me, FightField.fightHum);

  -- 扔符咒
  if self.step == 1 then
    if self:CheckInterval() then
      local randomRange = math.random(FUZHOU_RANDOM_RANGE * -1,FUZHOU_RANDOM_RANGE);
      local shiftX = randomRange;
      local shiftY = randomRange / 2 * -1;
      skill:SetLightWithOrder(fuZhouIndex, EEffectLightKind.Sky1, 11094, 100, 1, 17, 17, EEffectLightTracer.Line, me.nowPos.x + shiftX, me.nowPos.y + shiftY, 64, false, 0, target.nowPos.x + shiftX, target.nowPos.y + shiftY, 0.3);
      self.interval = FUZHOU_PERIOD;
      fuZhouIndex = fuZhouIndex + 1;
    end
    if fuZhouIndex == FUZHOU_AMOUNT then
      fuZhouIndex = 0;
      self.interval = 2;
      self.step = 2;
    end
  -- 爆炸
  elseif self.step == 2 then
    if self:CheckInterval() then
      skill:SetLightWithOrder(1, EEffectLightKind.Sky1, 11093, 100, 1, 17, 17, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y + 100, 64, false, 0, target.nowPos.x, target.nowPos.y + 100, 0);
      self.interval = 1.7;
      self.step = 3;
    end
  -- 上buff
  elseif self.step == 3 then
    if self:CheckInterval() then
      for key, value in pairs(enemy) do
        skill:SetLightWithOrder(key, EEffectLightKind.Sky1, 11095, 250, 1, 9, 9, EEffectLightTracer.StandAni, enemy[key].nowPos.x, enemy[key].nowPos.y + 60, 64, false, 0, enemy[key].nowPos.x, enemy[key].nowPos.y + 60, 0);
      end
      self.interval = 2.5;
      self.step = 4;
    end
  -- 結束
  elseif self.step == 4 then
    if self:CheckInterval() then
      self.isLightEnd = true;
    end
  end
end

function DarkCommand.GetEnemy(me,fightHum)
  local enemy = {};
  if me.party_Kind == EFightParty.Right then
    for index, data in pairs(fightHum) do
      if index <=9 then
        table.insert(enemy,fightHum[index]);
      end
    end
  else
    for index, data in pairs(fightHum) do
      if index >=10 then
        table.insert(enemy,fightHum[index]);
      end
    end
  end
  return enemy;
end
