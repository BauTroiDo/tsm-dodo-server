QuakeLightning = InheritsAttack(RandomHit)

function QuakeLightning:PlayEffectLight()
  local target = FightField.fightHum[self.aimIdx]
  local me = FightField.fightHum[self.humIdx]
  if target.party_Kind == EFightParty.Left then
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10430, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x - 5, target.nowPos.y, 30, false, 40);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10431, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x, target.nowPos.y, 30, false, 40);
    end
  else
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10431, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x, target.nowPos.y, 30, false, 40);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10430, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x - 5, target.nowPos.y, 30, false, 40);
    end
  end
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10328, 30, 1, 7, 7, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 50);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10342, 30, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 40);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky4, 10441, 30, 1, 4, 12, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 0);
end

--專武震電
EW_QuakeLightning = InheritsAttack(QuakeLightning);

function EW_QuakeLightning:InitDrawBlack()
  self.needDrawBlack = true;
end

EW_QuakeFire = InheritsAttack(RandomHit)

function EW_QuakeFire:InitDrawBlack()
  self.needDrawBlack = true;
end

function EW_QuakeFire:PlayEffectLight()
  local target = FightField.fightHum[self.aimIdx]
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10977, 30, 1, 7, 7, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 50);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10102, 30, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 40);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky4, 10440, 30, 1, 4, 12, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 0);
end

