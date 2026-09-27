Dis4Status = InheritsFromBaseAttackSkill();

function Dis4Status:Update() 
  local me = FightField.fightHum[self.humIdx];
  local target;
  local attack = FightField.attack[self.humIdx];
  if self.step == 1 then     
    attack:SetLightWithOrder(1, EEffectLightDis.Ground,10070,50,1,32,32, EEffectLightTracer.StandAni, me.nowPos.x+7, me.nowPos.y,20, true, 50);     --咒語
    self.interval = 1;
    self.step = 2;              
  elseif self.step == 2 then       
    if self:CheckInterval() then
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[ self.emyIdxAy[i] ];
        attack:SetLightWithOrder(2 + i, EEffectLightDis.Body, 10094,80,1,19,19, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y,30, true, 60);                    
      end
      self.step = 3;
    end    
  elseif self.step == 3 then       
    if self.emyNum == 0 then        
      self.step = 4;
      return;
    end

    if attack.lights[2].picId == 0 then
      self.step = 4;
    end
  elseif self.step == 4 then 
    self.isLightEnd = true;
    self.step = 5;
  end
end