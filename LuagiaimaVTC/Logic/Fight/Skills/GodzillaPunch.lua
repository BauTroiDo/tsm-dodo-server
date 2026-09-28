GodzillaPunch = InheritsFromBaseAttackSkill();

function GodzillaPunch:Update()
    local me = FightField.fightHum[self.humIdx];
    local target = FightField.fightHum[self.tarIdx];

    if self.step == 1 then
        me.roleController:OneHandAttack();
        me:PlaySound(target.colm, target.row, Attack_Start);
        self.step = 2;
    elseif self.step == 2 then
        if me.roleController.animationFrame >=2 then
            for i = 0, self.emyNum-1 do
                target = FightField.fightHum[ self.emyIdxAy[i] ];
                me:PlaySound(target.colm, target.row, Attack_Hit);
                me:PlayEffect(target.colm, target.row, ERolePose.Hit);
                FightField.attack[self.humIdx]:SetLightWithOrder( i * 2, EEffectLightDis.Body, 10140, 80, 3, 15, 15, EEffectLightTracer.StandAni, target.nowPos.x,
                        target.nowPos.y + 100, 20);
                FightField.attack[self.humIdx]:SetLightWithOrder( i * 2 +1, EEffectLightDis.Body, 10146, 80, 6, 1, 6, EEffectLightTracer.StandAni, target.nowPos.x,
                        target.nowPos.y + 60, 32);
            end
            self.interval = 1;
            self.step = 3;
        end
    elseif self.step == 3 then
        if self:CheckInterval() then
            self.isLightEnd = true;
        end
    end
end
