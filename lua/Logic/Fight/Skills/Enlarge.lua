Enlarge = InheritsFromBaseAttackSkill();

function Enlarge:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then           
    me.roleController:MagicAttack();
    --030601J風魔法陣
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground,10077,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+5, me.nowPos.y,20, true, 90);
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10003, 50,1,25,25, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y,20, true, 20);
    self.step = 2;                  
  elseif self.step == 2 then                     
    if FightField.attack[self.humIdx]:CheckLightIsEnd(EEffectLightKind.Sky1)then        
      self.isLightEnd = true;
      self.step = 3;                  
    end
  end
end

--狀態顯示
EnlargeMe = InheritsFromBaseStatus();

function EnlargeMe:InitExtraParameters()
  self.enlargeOldTime = CGTimer.time;  

  self.size = FightField.fightHum[self.humIdx].shrink + 5;   --放大的尺寸
  if self.size > MaxShrink then
    self.size = MaxShrink;
  end

   --放大的尺寸
  if FightField.fightHum[self.humIdx].shrink == DefShrink then 
    self.size = MaxShrink;
  elseif FightField.fightHum[self.humIdx].shrink == MaxShrink then 
    self.size = MaxShrink;
  elseif FightField.fightHum[self.humIdx].shrink == MinShrink then 
    self.size = DefShrink;
  end
end

function EnlargeMe:ExtraClose()
  FightField.fightHum[self.humIdx]:SetShrink(DefShrink);
end

function EnlargeMe:Update()
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then     
    me:GetStatus(self.statusKind):SetLight(EEffectLightKind.Body1,10003, 50,1,25,25, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y,20, true, 20);              
    self.step = 2;              
  elseif self.step == 2 then       
    if (CGTimer.time - self.enlargeOldTime) * 1000 * FightField.timeScale >= 100 then        
      if me.shrink < MaxShrink then          
        if me.shrink ~= self.size then
          me:SetShrink(me.shrink+1);
        end    
      else
        self.step = 3
      end
      self.enlargeOldTime = CGTimer.time;        
    end
  end
end