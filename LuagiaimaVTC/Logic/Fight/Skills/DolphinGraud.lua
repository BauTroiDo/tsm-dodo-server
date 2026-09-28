DolphinGraud = InheritsFromBaseAttackSkill();

function DolphinGraud:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    me.roleController:SetAnimationForceId(ERolePose.Magic);
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10077, 50, 1, 21, 21, EEffectLightTracer.StandAni, me.nowPos.x + 5, me.nowPos.y, 20, true, 90);
    self.interval = 0.8;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Body, 10387, 100, 1, 17, 17, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 20, true, 30);
      FightField.attack[self.humIdx].lights[2]:SetScale(Vector3.New(0.5, 0.5, 1));
      me:PlaySound(target.colm, target.row);         
      self.interval = 0.8;
      self.step = 3;
    end
  elseif self.step == 3 then
    if self:CheckInterval() then 
      self.isLightEnd = true;
      self.step = 4;
    end
  end
end

DolphinGraudMe = InheritsFromBaseStatus();

function DolphinGraudMe:ExtraClose()  
  FightField.RestoreFightInfo(self.humIdx, 0);
end

function DolphinGraudMe:Update()  
  if self.step == 1 then
    local me = FightField.fightHum[self.humIdx];

    me:GetStatus(self.statusKind):SetLightWithOrder(1, EEffectLightDis.Body, 10387, 100, 1, 17, 17, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y, 20, true, 30);                                                 
    me:GetStatus(self.statusKind).lights[1]:SetScale(Vector3.New(0.5, 0.5, 1));
    me:SetStatusKind(self.statusKind, EStatus.DolphieGraud);
    self.step = 2;    
  end
end
