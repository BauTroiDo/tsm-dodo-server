WindFaster = InheritsFromBaseAttackSkill();

function WindFaster:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    me.roleController:SetAnimationForceId(ERolePose.Magic);
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10077, 35, 1, 21, 21, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 20, true, 80);
    self.interval = 0.8;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      for i=0, self.emyNum-1 do
        target = FightField.fightHum[self.emyIdxAy[i]];
        if target ~= nil then 
          FightField.attack[self.humIdx]:SetLightWithOrder(2 + i, EEffectLightDis.Body, 10282, 100, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 80);
        end        
      end

      self.interval = 1;
      self.step = 3;
    end
  elseif self.step == 3 then
    if self:CheckInterval() then 
      self.isLightEnd =true;
      self.step = 4;
    end
  end
end

--狀態顯示
WindFasterMe = InheritsFromBaseStatus();

function WindFasterMe:ExtraClose()  
  FightField.RestoreFightInfo(self.humIdx,0);
end

function WindFasterMe:Update()  
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then   
    me:GetStatus(self.statusKind):SetLightWithOrder(2, EEffectLightDis.Sky, 10282, 100, 1, 10, 10, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y, 30, true, 80);

    self.step = 2;
  end
end