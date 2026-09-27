RagFire = InheritsFromBaseAttackSkill();

function RagFire:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    local newPos = target:GetSpot(EFightSpot.Front, 60);
    me:SetWalk(newPos.x, newPos.y);
    me.roleController:SetInBattle(false);
    me.roleController:SetIsMoving(true);
    me:SetSpeed(1);
    self.step = 2;
  elseif self.step == 2 then
    if me.arrive then
      me.roleController:MagicAttack();
      me.roleController:FaceTo(target.nowPos);
      self.step = 3;
    end
  elseif self.step == 3 then
    if me.roleController.animationFrame == 1 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10037, 30, 1, 24, 24, EEffectLightTracer.StandAni, target.nowPos.x + 5, target.nowPos.y, 35, true, 20);
      self.step = 4;
    end
  elseif self.step == 4 then
    if me.roleController.animationFrame == 0 then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10055, 30, 1, 21, 21, EEffectLightTracer.StandAni, target.nowPos.x + 5, target.nowPos.y, 35, true, 30);
      me:PlaySound(target.colm, target.row);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit);
      
      me.roleController:SetIsMoving(false);
      me.roleController:SetIsJumping(true);
      me:SetJump(FightField.chip[me.colm][me.row].x, FightField.chip[me.colm][me.row].y, true);
      self.step = 5;
    end
  elseif self.step == 5 then
    if me.arrive then
      self.isLightEnd = true;
      self.step = 6;
    end
  end
end