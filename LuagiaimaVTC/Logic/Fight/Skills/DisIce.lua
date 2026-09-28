DisIce = InheritsFromBaseAttackSkill();

function DisIce:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    me.roleController:MagicAttack();
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground ,10070, 50, 1, 32, 32, EEffectLightTracer.StandAni, me.nowPos.x + 7, me.nowPos.y, 20, true, 50);
    self.interval = 1;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then     
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky ,10109, 50, 1, 26, 26, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 70);
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky ,10045, 80, 14, 26, 26, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 35);
      self.step = 3;
    end
  elseif self.step == 3 then
    if FightField.attack[self.humIdx].lights[2].picId == 0 then
      self.isLightEnd = true;
      self.step = 4;
    end
  end
end