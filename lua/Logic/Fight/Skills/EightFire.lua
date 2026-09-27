EightFire = InheritsFromBaseAttackSkill();

function EightFire:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then
    local newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:MagicAttack();
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10064, 40, 1, 17, 17, EEffectLightTracer.StandAni, me.nowPos.x + 12, me.nowPos.y, 27, true, 110);
    self.step = 2;
  elseif self.step == 2 then
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].picId == 0 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10126, 40, 1, 11, 11, EEffectLightTracer.StandAni, me.nowPos.x - 10, me.nowPos.y, 27, true, 80);
      self.step = 3;
    end
  elseif self.step == 3 then
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10098, 50, 1, 9, 9, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 27, true, 160);
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10099, 100, 1, 9, 9, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 27, true, 80);

    me:PlaySound(target.colm, target.row);
    me:PlayEffect(target.colm, target.row, ERolePose.Hit);
    self.step = 4;
  elseif self.step == 4 then 
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky3].picId == 0 then
      self.isLightEnd = true;
      self.step = 5;
    end
  end
end