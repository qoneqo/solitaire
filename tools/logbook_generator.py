"""
Solitaire Logbook Generator (100% Winnable)
===========================================
This script generates a 'logbook.json' containing deck configurations 
that are guaranteed to be solvable by the InternalSolver.

Usage: python logbook_generator.py
Output: ../app/src/main/assets/solitaire/logbook.json
"""

import json
import random
from copy import deepcopy

SUITS = ['c', 'd', 'h', 's']
SUIT_COLORS = {'c': 'B', 'd': 'R', 'h': 'R', 's': 'B'}

def color(suit):
    return SUIT_COLORS[suit]

def can_foundation(card, pile):
    if not pile: return card['rank'] == 1
    top = pile[-1]
    return card['suit'] == top['suit'] and card['rank'] == top['rank'] + 1

def can_tableau(card, pile):
    if not pile: return card['rank'] == 13
    top = pile[-1]
    if not top['up']: return False
    return color(card['suit']) != color(top['suit']) and card['rank'] == top['rank'] - 1

def generate_random_deck():
    all_cards = [(s, r) for s in SUITS for r in range(1, 14)]
    random.shuffle(all_cards)
    
    deck = []
    # 28 cards for tableaus (col 0-6)
    for i in range(28):
        c = all_cards[i]
        deck.append(f"{c[0]}{c[1]}")
    # 24 cards for stock
    for i in range(28, 52):
        c = all_cards[i]
        deck.append(f"{c[0]}{c[1]}")
    return deck

def simulate_solve(deck_strings, max_moves=1000):
    """Mirror of InternalSolver.kt and GameSurfaceView.kt logic"""
    
    # Setup
    deck = [{'suit': s[0], 'rank': int(s[1:]), 'up': False} for s in deck_strings]
    tableaus = [[] for _ in range(7)]
    idx = 0
    for i in range(7):
        for j in range(i + 1):
            c = dict(deck[idx]); c['up'] = (j == i)
            tableaus[i].append(c); idx += 1
            
    # Stock order fix: app now reverses the deck list for stock
    stock_part = deck[idx:]
    stock = list(reversed([dict(c) for c in stock_part])) 
    
    state = {'tableaus': tableaus, 'stock': stock, 'waste': [], 'foundations': [[], [], [], []]}
    
    def get_move(st):
        # P1: To Foundation
        if st['waste']:
            if can_foundation(st['waste'][-1], st['foundations'][0]): return ('TF', 'W', 0)
            if can_foundation(st['waste'][-1], st['foundations'][1]): return ('TF', 'W', 1)
            if can_foundation(st['waste'][-1], st['foundations'][2]): return ('TF', 'W', 2)
            if can_foundation(st['waste'][-1], st['foundations'][3]): return ('TF', 'W', 3)
        for i in range(7):
            if st['tableaus'][i] and st['tableaus'][i][-1]['up']:
                for j in range(4):
                    if can_foundation(st['tableaus'][i][-1], st['foundations'][j]): return ('TF', 'T', i, j)
        # P2: Reveal Hidden
        for j in range(7):
            pile = st['tableaus'][j]
            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
            if first_up <= 0: continue
            for i in range(7):
                if i != j and can_tableau(pile[first_up], st['tableaus'][i]):
                    return ('TT', 'T', j, i, len(pile) - first_up)
        # P3: Waste to Tableau
        if st['waste']:
            for i in range(7):
                if can_tableau(st['waste'][-1], st['tableaus'][i]): return ('TT', 'W', -1, i, 1)
        # P4: Deal Stock
        if st['stock']: return ('DS', None)
        # P5: Tableau to Tableau
        for j in range(7):
            pile = st['tableaus'][j]
            if not pile: continue
            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
            if first_up == -1: continue
            for i in range(7):
                if i != j and st['tableaus'][i] and can_tableau(pile[first_up], st['tableaus'][i]):
                    return ('TT', 'T', j, i, len(pile) - first_up)
        # P6: King to empty
        has_hidden = any(any(not c['up'] for c in p) for p in st['tableaus'])
        if has_hidden:
            for i in range(7):
                if not st['tableaus'][i]:
                    if st['waste'] and st['waste'][-1]['rank'] == 13: return ('TT', 'W', -1, i, 1)
                    best_f, best_h = -1, 0
                    for j in range(7):
                        pile = st['tableaus'][j]
                        if pile and pile[0]['up'] == False: # has hidden
                            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
                            if first_up > 0 and pile[first_up]['rank'] == 13 and first_up > best_h:
                                best_h, best_f = first_up, j
                    if best_f != -1: return ('TT', 'T', best_f, i, len(st['tableaus'][best_f]) - next(k for k, c in enumerate(st['tableaus'][best_f]) if c['up']))
        # P7: Recycle
        if st['waste'] and not st['stock']: return ('RW', None)
        return None

    recycle_count = 0
    for move_num in range(max_moves):
        if all(len(f) == 13 for f in state['foundations']): return True
        move = get_move(state)
        if not move: return False
        
        action = move[0]
        if action == 'DS':
            c = state['stock'].pop(); c['up'] = True; state['waste'].append(c)
        elif action == 'RW':
            recycle_count += 1
            if recycle_count >= 3: return False
            state['stock'] = list(reversed(state['waste']))
            for c in state['stock']: c['up'] = False
            state['waste'] = []
        elif action == 'TF':
            recycle_count = 0
            if move[1] == 'W':
                state['foundations'][move[2]].append(state['waste'].pop())
            else:
                fi, ti = move[2], move[3]
                state['foundations'][ti].append(state['tableaus'][fi].pop())
                if state['tableaus'][fi] and not state['tableaus'][fi][-1]['up']: state['tableaus'][fi][-1]['up'] = True
        elif action == 'TT':
            if move[1] == 'W':
                state['tableaus'][move[3]].append(state['waste'].pop())
            else:
                fi, ti, cnt = move[2], move[3], move[4]
                stack = state['tableaus'][fi][-cnt:]
                state['tableaus'][fi] = state['tableaus'][fi][:-cnt]
                if state['tableaus'][fi] and not state['tableaus'][fi][-1]['up']: state['tableaus'][fi][-1]['up'] = True
                state['tableaus'][ti].extend(stack)
    return False

def main():
    print("Generating 100 winnable games...")
    logbook = []
    wins = 0
    attempts = 0
    
    while wins < 100:
        attempts += 1
        deck = generate_random_deck()
        if simulate_solve(deck):
            wins += 1
            logbook.append({
                "id": wins,
                "deck": deck,
                "moves": [{"type": "DEAL_STOCK"}] # Placeholder
            })
            print(f"  [{wins:3d}/100] Found winnable game after {attempts} attempts")
            
    output_path = 'app/src/main/assets/solitaire/logbook.json'
    with open(output_path, 'w') as f:
        json.dump(logbook, f, indent=2)
    print(f"\nSaved {wins} games to {output_path}")

if __name__ == '__main__':
    main()
