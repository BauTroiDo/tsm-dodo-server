FateStrike = InheritsFromBaseAttackSkill();

function FateStrike:Update()
  local skill = FightField.attack[self.humIdx];
  if self.step == 1 then
    for i = 0, self.emyNum - 1 do
      local target = FightField.fightHum[self.emyIdxAy[i]];
      skill:SetLightWithOrder(i, EEffectLightKind.Sky1, 11096, 100, 1, 18, 18, EEffectLightTracer.StandAni, target
        .nowPos.x, target.nowPos.y + 100, 64, false, 0, target.nowPos.x, target.nowPos.y + 100);
    end
    self.interval = 2.5;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then
      self.isLightEnd = true;
    end
  end
end
