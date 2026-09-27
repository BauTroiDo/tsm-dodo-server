Vitalit = InheritsFromBaseAttackSkill();

function Vitalit:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then             
    me.roleController:MagicAttack();
    --風魔法陣
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground ,10077,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+5, me.nowPos.y,20, true, 90);

    self.interval = 0.8;
    self.step = 2;              
  elseif self.step == 2 then       
    if self:CheckInterval() then        
      --被施法光影
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky,10001, 80, 1,15, 15, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 40);
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky,10001, 60, 1,15, 15, EEffectLightTracer.StandAni, target.nowPos.x-10, target.nowPos.y, 30, true, 40);

      self.step = 3;                  
    end
  elseif self.step == 3 then     
    if FightField.attack[self.humIdx]:CheckLightIsEnd(2) then 
      self.step = 4;
      self.isLightEnd = true;
    end    
  end
end