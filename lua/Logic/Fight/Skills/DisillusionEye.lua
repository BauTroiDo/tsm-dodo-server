DisillusionEye = InheritsFromBaseAttackSkill();

function DisillusionEye:Update()
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  local attack = FightField.attack[self.humIdx];
  if self.step == 1 then
    me.roleController:MagicAttack();
    me.roleController:SetAnimationForceId(ERolePose.Magic);
    attack:SetLightWithOrder(3, EEffectLightDis.Ground, 11115, 100, 1, 10, 10, EEffectLightTracer.Standani, me.nowPos.x + 10, me.nowPos.y, 15, true, 60);
    attack:SetLightWithOrder(4, EEffectLightDis.Sky, 11116, 150, 1, 9, 9, EEffectLightTracer.Standani, me.nowPos.x + 10, me.nowPos.y, 20, true, 80);
    self.interval = 0.9;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then
      attack:SetLightWithOrder(2, EEffectLightDis.Sky, 11117, 90, 1, 18, 18, EEffectLightTracer.StandAni, 0, 27, 25, false, 160);
      self.step = 3;
    end
  elseif self.step == 3 then
    if attack.lights[2].picId == 0 then
      me:PlaySound(target.colm, target.row, Attack_Start);
      newPos = Vector2.New(0, 27);
      attack:SetLightWithOrder(1, EEffectLightDis.Sky, 11113, 50, 1, 17, 17, EEffectLightTracer.StandAni, newPos.x + 15, newPos.y, 25, true, 6);
      attack:SetLightWithOrder(2, EEffectLightDis.Sky, 11118, 100, 1, 8, 8, EEffectLightTracer.Stand, newPos.x, newPos.y, 25, true, 160);
      self.step = 4;
    end
  elseif self.step == 4 then
    if attack.lights[1].picId == 0 then
      me:PlaySound(target.colm, target.row, Attack_Start);
      newPos = Vector2.New(0, 27);
      attack:SetLightWithOrder(1, EEffectLightDis.Sky, 11114, 50, 1, 11, 11, EEffectLightTracer.StandAni, newPos.x + 15, newPos.y, 25, true, 6);
      self.step = 5;
    end
  elseif self.step == 5 then
    if attack.lights[1].picId == 0 then
      newPos = Vector2.New(0, 27);
      attack:SetLightWithOrder(1, EEffectLightDis.Sky, 11119, 120, 1, 12, 12, EEffectLightTracer.StandAni, newPos.x + 15, newPos.y, 25, true, 106);
      me:PlaySound(target.colm, target.row, Attack_Start, 2);
      for i = 0, self.emyNum - 1 do
        target = FightField.fightHum[self.emyIdxAy[i]];
        --放狀解的光影
        attack:SetLightWithOrder(5 + i * 3, EEffectLightDis.Body, 11110, 80, 1, 19, 19, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 60);
        if me.party_Kind == target.party_Kind then
          attack:SetLightWithOrder(6 + i * 3, EEffectLightDis.Body, 11112, 70, 1, 17, 17, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 40);
          attack:SetLightWithOrder(7 + i * 3, EEffectLightDis.Body, 11111, 70, 1, 14, 14, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 35, true, 50);
        end
      end
      self.step = 6;
    end
  elseif self.step == 6 then
    if attack.lights[1].picId == 0 then
      self.interval = 0.06;
      self.step = 7;
    end
  elseif self.step == 7 then
    if self:CheckInterval() then
      self.interval = 0.06;
      attack.lights[2].alpha = attack.lights[2].alpha - 1;
      attack.lights[2].rawImage.material:SetFloat("_InvisibleAlpha", attack.lights[2].alpha / 32);
    end

    if attack.lights[2].alpha == 1 then
      self.step = 8;
    end
  elseif self.step == 8 then
    self.isLightEnd = true;
    self.step = 9;
  end
end
