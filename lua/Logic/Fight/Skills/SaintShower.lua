SaintShower = InheritsFromBaseAttackSkill();

function SaintShower:Update()
  local skill = FightField.attack[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then
    for i = 0, self.emyNum - 1 do
      local target = FightField.fightHum[self.emyIdxAy[i]];
      skill:SetLightWithOrder(i, EEffectLightKind.Sky1, 20061, 333, 1, 6, 6, EEffectLightTracer.StandAni,
        target.nowPos.x, target.nowPos.y + 50, 64, false, 0, target.nowPos.x, target.nowPos.y + 100);
    end
    self.interval = 2.0;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then
      self.isLightEnd = true;
    end
  end
end
