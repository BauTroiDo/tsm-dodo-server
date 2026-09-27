Poor = InheritsFromBaseAttackSkill();

function Poor:ExtraClose()
  FightField.fightHum[self.tarIdx]:SetStatus(EFightStatusKind.Faint, ESkill_Kind.Poor); --給予狀態
end

function Poor:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  
  if self.step == 1 then             
    me.roleController:MagicAttack();
    --030601J 魔法陣
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10082, 50, 1, 20, 20, EEffectLightTracer.Stand, me.nowPos.x +6, me.nowPos.y, 30, false, 70);
  
    self.interval = 0.8;
    self.step = 2;
  elseif self.step == 2 then           
    if self:CheckInterval() then                  
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky,10151, 100,1,4,4, EEffectLightTracer.Line, me.nowPos.x, me.nowPos.y, 255, true, 0, target.nowPos.x, target.nowPos.y, 0.28);      
      self.step = 3;            
    end
  elseif self.step == 3 then      
    if FightField.attack[self.humIdx].lights[2].isArrive then        
      me:PlaySound(target.colm, target.row);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit);
      self.step = 4;                  
      self.isLightEnd = true;
    end
  end      
end


--狀態顯示
PoorMe = InheritsFromBaseStatus();

function PoorMe:Update()
  if self.step == 1 then 
    local me = FightField.fightHum[self.humIdx];        
      me.status[self.statusKind]:SetLight(1,10152,100,1,4,4, EEffectLightTracer.Stand, me.nowPos.x+10, me.nowPos.y,255, true, 0);      
      self.step = 2;              
  end
end