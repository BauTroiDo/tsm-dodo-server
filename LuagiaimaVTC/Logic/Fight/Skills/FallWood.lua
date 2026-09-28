FallWood = InheritsFromBaseAttackSkill();

function FallWood:Update()  
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then    
    local newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:MagicAttack();
    --030601J土魔法陣
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground,10080,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+6, me.nowPos.y,20, true, 80);

    self.interval = 0.8;
    self.step = 2;              
  elseif self.step == 2 then       
    if self:CheckInterval() then        
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10024, 60, 1,14, 14, EEffectLightTracer.StandAni,
                                           target.nowPos.x, target.nowPos.y, 255, false, 15);
      self.step = 3;                  
    end
  elseif self.step == 3 then       
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].orderCount == 4 then        
      me:PlayEffect(target.colm, target.row, ERolePose.Hit); 
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10016, 60, 1,15, 15, EEffectLightTracer.StandAni,
                                                 target.nowPos.x, target.nowPos.y+30, 30, false, 15);
      me:PlaySound(target.colm, target.row);   
      FightField.SetShake(true);
      self.step = 4;        
    end
  elseif self.step == 4 then     
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].orderCount == 6 then        
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10016, 20, 1, 6, 15,EEffectLightTracer.StandAni,
                                                 target.nowPos.x-40, target.nowPos.y-20, 20, false, 15);
      FightField.SetShake(false);
      self.step = 5;           
    end
  elseif self.step == 5 then   
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].orderCount == 10 then        
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 10016, 30, 1,15, 15, EEffectLightTracer.StandAni,
                                                 target.nowPos.x+60, target.nowPos.y+20, 25, false, 15);
      self.step = 6;                                                                  
    end
  elseif self.step == 6 then   
    if (FightField.attack[self.humIdx].lights[EEffectLightKind.Sky2].orderCount==14) then      
      self.isLightEnd = true;
      self.step = 7;
    end
  end
end