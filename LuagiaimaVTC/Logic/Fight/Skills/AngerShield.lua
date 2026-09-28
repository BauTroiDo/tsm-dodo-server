AngerShield = InheritsFromBaseAttackSkill();

function AngerShield:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10434, 60, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 50);
    if target.party_Kind == EFightParty.Left then
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, 10522, 60, 1, 6, 6, EEffectLightTracer.Stand, target.nowPos.x, target.nowPos.y, 30, true, 30);
    else
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, 10523, 60, 1, 6, 6, EEffectLightTracer.Stand, target.nowPos.x, target.nowPos.y, 30, true, 30);
    end
    
    me:PlayEffect(target.colm, target.row, ERolePose.Prepare);
    self.interval = 0.8;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      self.isLightEnd = true;
      self.step = 3;
    end
  end
end

--狀態顯示
AngerShieldMe = InheritsFromBaseStatus();

function AngerShieldMe:ExtraClose()  
  FightField.RestoreFightInfo(self.humIdx,0);
end

function AngerShieldMe:Update()  
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then     
    if me.party_Kind == EFightParty.Left then
      me:GetStatus(self.statusKind):SetLight(EEffectLightKind.Body1, 10522, 30, 1, 6, 6, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y, 30, true, 30);
    else
      me:GetStatus(self.statusKind):SetLight(EEffectLightKind.Body1, 10523, 30, 1, 6, 6, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y, 30, true, 30);
    end
    self.step = 2;
  end
end