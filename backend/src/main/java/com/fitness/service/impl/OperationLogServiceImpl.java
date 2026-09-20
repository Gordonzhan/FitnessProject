package com.fitness.service.impl;

import com.fitness.entity.OperationLog;
import com.fitness.mapper.OperationLogMapper;
import com.fitness.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class OperationLogServiceImpl implements OperationLogService {
    
    @Autowired
    private OperationLogMapper operationLogMapper;
    
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveLog(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteExpiredBefore(Date cutoff) {
        return operationLogMapper.deleteBefore(cutoff);
    }
}
