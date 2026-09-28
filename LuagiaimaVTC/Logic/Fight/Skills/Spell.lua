--符咒
Spell = InheritsFromBaseAttackSkill();

function Spell:Update()
  local me = FightField.fightHum[self.humIdx];  
  local target = FightField.fightHum[self.tarIdx]
  me:PlayEffect(target.colm, target.row, ERolePose.Prepare);
  self.isLightEnd = true;
end

--狀態顯示
SpellMe = InheritsFromBaseStatus();

function SpellMe:Update()
  local me = FightField.fightHum[self.humIdx];        
  if self.step == 1 then     
      me.status[self.statusKind]:SetLight(1,10159, 40,1,19, 19, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 25, false, 20);     
      self.step = 2;              
  elseif self.step == 2 then 
    if me.status[self.statusKind]:CheckLightIsEnd(1) then 
      self.step = 3;
      self.isLightEnd = true;
    end
  end
end