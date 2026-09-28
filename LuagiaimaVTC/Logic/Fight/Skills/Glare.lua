Glare = InheritsAttack(QuakeLightning)

function Glare:PlayEffectLight()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.aimIdx];
  if target.party_Kind == EFightParty.Left then
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10835, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x - 5, target.nowPos.y, 30, false, 40);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10836, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x, target.nowPos.y, 30, false, 40);
    end
  else
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10836, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x, target.nowPos.y, 30, false, 40);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10835, 30, 1, 5, 5, EEffectLightTracer.StandAni, me.nowPos.x - 5, target.nowPos.y, 30, false, 40);
    end
  end
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10837, 30, 1, 7, 7, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 50);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10838, 30, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 40);
  FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky4, 10839, 30, 1, 4, 12, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 0);
end