SwordWillShatterSky = InheritsFromBaseAttackSkill();

function SwordWillShatterSky:Update()
  local me = FightField.fightHum[self.humIdx];
  local skill = FightField.attack[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then
    local  newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:SetAnimationForceId(ERolePose.Magic);
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10077, 50, 1, 21, 21, EEffectLightTracer.StandAni, me.nowPos.x + 5, me.nowPos.y, 20, true, 90);
    self.interval = 0.5;
    self.step = 2;
  elseif self.step == 2 then
    for i = 0, self.emyNum - 1 do
      target = FightField.fightHum[self.emyIdxAy[i]];
      skill:SetLightWithOrder(i, EEffectLightKind.Sky1, 11059, 150, 1, 10, 10, EEffectLightTracer.StandAni,
        target.nowPos.x, target.nowPos.y + 50, 64, false, 0, target.nowPos.x, target.nowPos.y + 100);
    end
    self.interval = 1.5;
    self.step = 3;
  elseif self.step == 3 then
    if self:CheckInterval() then
      self.isLightEnd = true;
    end
  end
end
