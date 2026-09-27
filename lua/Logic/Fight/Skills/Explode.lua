Explode = InheritsFromBaseAttackSkill();

function Explode:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then             
    me.roleController:OneHandAttack();
    if me.party_Kind == EFightParty.Right then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10708, 50, 1, 12, 12, EEffectLightTracer.Curve,
                                             me.nowPos.x, me.nowPos.y, 255, true, 0,
                                             target.nowPos.x, target.nowPos.y, 0.36)        --炸藥
    else
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10709, 50, 1, 12, 12, EEffectLightTracer.Curve,
                                             me.nowPos.x, me.nowPos.y, 255, true, 0,
                                             target.nowPos.x, target.nowPos.y, 0.36)        --炸藥
    end

    me.roleController:SetAnimationForceFrame(1) --鎖定動作
    self.step = 2;              
  elseif self.step == 2 then       
    --判斷光影到達位置
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].showPos == target.nowPos then         
      me:PlayEffect(target.colm, target.row, ERolePose.Hit, 1);

      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10104, 50, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y+50, 30)--爆炸
      me:PlaySound(target.colm, target.row);   

      self.interval = 0.15;
      self.step = 3;        
    end
  elseif self.step == 3 then       
    if self:CheckInterval() then        
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10289, 50, 13, 1, 13, EEffectLightTracer.StandAni,
                                             target.nowPos.x, target.nowPos.y+90,30);     --煙
      self.interval = 0.5;
      self.step = 4;                                                                                          
    end
  elseif self.step == 4 then       
    if self:CheckInterval() then        
      me.roleController:SetAnimationForceFrame(255) --還原鎖定動作                   
      self.isLightEnd = true; 
      self.step = 5;
    end  
  end
end