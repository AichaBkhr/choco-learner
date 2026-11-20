#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Mon Oct  5 11:30:27 2020

@author: nassim
"""


import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score

import numpy as np

data = pd.read_csv('LOG/data.log')
data_true= data[data['label']==True]
data_false= data[data['label']==False]
X=data_true
Y=data_true[data_true.columns[-1]]
del X['label']
X_train,X_test,Y_train,Y_test = train_test_split(X, Y, test_size=0.8, shuffle=False)
X=data_false
Y=data_false[data_false.columns[-1]]
del X['label']
X_train_,X_test_,Y_train_,Y_test_ = train_test_split(X, Y, test_size=0.8, shuffle=False)

data1 = pd.read_csv('LOG/kappa.log')
data_true1= data1[data1['label']==True]
data_false1= data1[data1['label']==False]
X1=data_true1
Y1=data_true1[data_true1.columns[-1]]
del X1['label']
X_train1,X_test1,Y_train1,Y_test1 = train_test_split(X1, Y1, test_size=0.8, shuffle=False)
X1=data_false1
Y1=data_false1[data_false1.columns[-1]]
del X1['label']
X_train_1,X_test_1,Y_train_1,Y_test_1 = train_test_split(X1, Y1, test_size=0.8, shuffle=False)

cst_true=X_train['constraints']
cst_false=X_train_['constraints']
csts_true = []
csts_false = []

variables =[]
for i in range(16):
    variables.append(str(i))
    
constraints=['EqualXY','DifferentXY','LessXY','GreaterXY','GreaterEqualXY','LessEqualXY']

distinct_true=0
distinct_false=0
distinct = len(constraints)+len(variables)    

for i in variables:
    for csts in cst_true:
      for c in csts :
        if i in c :
            distinct_true +=1
            break
for i in variables:
    for csts in cst_false:
     for c in csts :
        if i in c :
            distinct_false +=1
            break

for i in constraints :
   for csts in cst_true:
      for c in csts.split('|') :
        if i in c :
            distinct_true +=1
            break  

for i in constraints:
    for csts in cst_false:
     for c in csts.split('|') :
        if i in c :
            distinct_false +=1
            break



def test(x,y,cst,cst_true):
    true_cst=0
    true_x=0
    true_y=0
    for csts in cst_true:
      for c in csts.split('|') :
        if x in c :
            true_x +=1
    
    for csts in cst_true:
      for c in csts.split('|') :
        if y in c :
            true_y +=1
    

    for csts in cst_true:
      for c in csts.split('|') :
        if cst in c :
            true_cst +=1
  

    return true_x,true_y,true_cst


label=[]
P=[]
P_=[]
for i in X_test1['constraints']:
    if i :
        p_true=[]
        p_false=[]
        for c in i.split('|'):
         if c :
            d=[]
            string=''
            for s in c.split('['):
                for s_ in s.split():
                    s_=s_.replace(']','')
                    if s_.isdigit():
                       d.append(s_)
            cst=c.split('[')[0]
            if len(d)>1:
                x,y,c=test(d[0],d[1],cst,cst_true)
                p_true.append((x+y+c)/distinct_true/1)
                x,y,c=test(d[0],d[1],cst,cst_false)
                p_false.append((x+y+c)/distinct_false/1)
            else:
                x,y,c=test(d[0],'',cst,cst_true)
                p_true.append((x+y+c)/distinct_true/1)
                x,y,c=test(d[0],'',cst,cst_false)
                p_false.append((x+y+c)/distinct_false/1)
    
            
    
        true=1
        false=1
        for p in p_true:
            true*=p
        for p_ in p_false:
            false*=p_
        
        P.append(true)
        P_.append(false)
        


#print(accuracy_score(Y_test_1,label))
for i in range(len(X_test1['constraints'])):
    for a in X_test1['constraints'].iloc[i].split('|'):
        print("==================")
        print(a)
        print("P is the probability of a constraint to be learned")
        print(P[i])
        print("P_ is the probability of a constraint to be banned")
        print(P_[i])
        if P[i]>=P_[i]:
            print("**********************")
            print(a)
            print("**********************")


    
