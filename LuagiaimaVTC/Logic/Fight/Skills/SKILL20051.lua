SKILL20051 = InheritsFromBaseAttackSkill();

function SKILL20051:Update()
  local target;  
  local me = FightField.fightHum[self.humIdx];
  local attack = FightField.attack[self.humIdx];
  if self.step == 1 then     
    --八卦魔法陣
    attack:SetLightWithOrder(1,EEffectLightDis.Ground,10952,50,1,22,22,EEffectLightTracer.StandAni, me.nowPos.x+5, me.nowPos.y,30, false, 113);
    self.interval = 0.8;
    self.step = 2;              
  elseif self.step == 2 then       
    if self:CheckInterval() then                  
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[ self.emyIdxAy[i] ];        
        attack:SetLightWithOrder( 2 + i * self.emyNum, EEffectLightDis.Body, 10953,80,1,10,10,EEffectLightTracer.Stand, target.nowPos.x, target.nowPos.y,50, true, 40);        
        target.roleController:SetInBattle(false);
        target:SetSpeed(1.00);
        target:SetBeh(EFightBeh.Rotate);     --設定行為 旋轉
      end

      self.interval = 1;
      self.step = 3;                  
    end
  elseif self.step == 3 then       
    if self.emyNum == 0 then        
      self.step = 4;
      return;          
    end

    if self:CheckInterval() then
      self.step = 4;          
    end
  elseif self.step == 4 then 
    self.isLightEnd = true;
    self.step = 5;
  end
end