--隱身
Limpid = InheritsFromBaseAttackSkill();

function Limpid:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  if self.step == 1 then     
    me.roleController:MagicAttack();
    --風魔法陣
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground,10077,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+5, me.nowPos.y,20, true, 90);     --咒語

    self.interval = 0.8;
    self.step = 2;      
  elseif self.step == 2 then       
    if self:CheckInterval() then        
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1,10003, 50,1,25,25, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y,20, true, 20);
      --不加挨姿
      self.step = 3;                
    end                                    
  elseif self.step == 3 then       
    if FightField.attack[self.humIdx]:CheckLightIsEnd(EEffectLightKind.Sky1) then          
      self.isLightEnd = true;
    end
  end
end

EW_Limpid = InheritsAttack(Limpid);

function EW_Limpid:InitDrawBlack()
  self.needDrawBlack = true;   
end

LimpidMe = InheritsFromBaseStatus();

function LimpidMe:ExtraClose()
  FightField.fightHum[self.humIdx]:SetTransparent(false);
end

function LimpidMe:Update()
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then        
    me:SetTransparent(true);
    me:GetStatus(self.statusKind):SetLight(EEffectLightKind.Sky1,10003, 50,1,25,25, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y,20, true, 20);                         
    self.step = 2;        
  elseif self.step == 2 then       
    if me:GetStatus(self.statusKind):CheckLightIsEnd(EEffectLightKind.Sky1) then
      me:GetStatus(self.statusKind):StopLight(Light_Sky1);                            
      self.step = 3;
    end
  end
end