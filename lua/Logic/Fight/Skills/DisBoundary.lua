--解結界
DisBoundary = InheritsFromBaseAttackSkill();

function DisBoundary:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then             
    me.roleController:MagicAttack();
    
    --030601J土魔法陣
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground,10080,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+6, me.nowPos.y,20, true, 80);

    self.interval = 0.8;
    self.step = 2;      
  elseif self.step == 2 then      
    if self:CheckInterval() then
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky,10127,80,1,13,13, EEffectLightTracer.StandAni, target.nowPos.x,target.nowPos.y,30, true, 80);
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky,10045,80,14,26,26, EEffectLightTracer.StandAni, target.nowPos.x,target.nowPos.y,30, true, 35);
      self.step = 3;                  
    end
  elseif self.step == 3 then     
    if FightField.attack[self.humIdx]:CheckLightIsEnd(2) then 
      self.step = 4;
      self.isLightEnd = true; --特技施展完畢
    end
  end
end