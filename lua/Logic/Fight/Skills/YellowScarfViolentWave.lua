YellowScarfViolentWave = InheritsFromBaseAttackSkill();

function YellowScarfViolentWave:LightPoint()
  local target = FightField.fightHum[self.tarIdx];
  if Contains(target.colm, 1, 2) then  --指定目標為前排
    return target:GetSpotOfRc(EFightSpot.Back, 30)
  else                                 --指定目標為後排
    return target:GetSpotOfRc(EFightSpot.Front, 30);
  end
end

function YellowScarfViolentWave:ShowHit()
  if not self:CheckInterval() then
    return 
  end

  local me = FightField.fightHum[self.humIdx];
  local target;
  for i = 0, self.emyNum-1 do
    if math.random(0, 1) == 0 then  
      target = FightField.fightHum[ self.emyIdxAy[i] ];
      if target.roleController.animationTrigger == EAnimationTrigger.Hit then
        target.roleController.animationTrigger = EAnimationTrigger.None;
      else    
        me:PlaySound(target.colm, target.row);
        target.roleController:Hit();
      end
    end
  end
  self.interval = 0.1;
end

function YellowScarfViolentWave:Update()    
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  local target;
  local attack = FightField.attack[self.humIdx];
  if self.step == 1 then     
    me.roleController:MagicAttack();               --做施法動作
    attack:SetLightWithOrder(1, EEffectLightDis.Sky, 10732, 130, 1, 9, 9, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y, 25, false, 35);
    newPos = self:LightPoint();                    --取水波出現位置
    attack:SetLightWithOrder(2, EEffectLightDis.Sky, 10736, 210, 1, 9, 9, EEffectLightTracer.StandAni, newPos.x, newPos.y, 25, true, 70);
    self.step = 2;      
  elseif self.step == 2 then 
    if attack.lights[2].orderCount == 2 then      
      FightField.SetShake(true);  --地震
      self:ShowHit();             --播挨打動作
      self.step = 3;
    end
  elseif self.step == 3 then 
    if attack.lights[2].orderCount == 8 then      
      for i = 0, self.emyNum-1 do        
        target = FightField.fightHum[ self.emyIdxAy[i] ];
        me:PlaySound(target.colm, target.row);            --音效
        me:PlayEffect(target.colm, target.row, ERolePose.Hit); --放血
      end
      FightField.SetShake(false);                     --地震-停
      attack:StopLight(1);                           --結束符咒光影
      self.step = 4;      
    else
      self:ShowHit();
    end
  elseif self.step == 4 then 
    self.step = 5;
    self.isLightEnd = true; --特技施展完畢
  end
end