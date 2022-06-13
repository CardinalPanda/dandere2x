
/*
    This file is part of the Dandere2x project.

    Dandere2x is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Dandere2x is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <https://www.gnu.org/licenses/>.
*/

/* 
========= Copyright aka_katto 2018, All rights reserved. ============
Original Author: aka_katto 
Date: 4/11/20 
Purpose: 
 
===================================================================== */


#include <algorithm>
#include "ExhaustiveSearch.h"
#include "../Block.h"
#include "../../../evaluator/MSE_Function.h"

//-----------------------------------------------------------------------------
// Purpose: Create a series points that represent a box surrounding the centx
//          and centy coordinates.
//-----------------------------------------------------------------------------
std::vector<Block::Point> ExhaustiveSearch::createSearchVector(int centx, int centy) {
    std::vector<Block::Point> list = std::vector<Block::Point>();

    // We can optimize this a bit further by restricting the search domain to be within the grid.

    Block::Point point{};
    point.x = centx;
    point.y = centy;
    list.push_back(point);

    return list;
}

//-----------------------------------------------------------------------------
// Purpose: todo
//-----------------------------------------------------------------------------
Block ExhaustiveSearch::match_block(const int x, const int y, const int block_size) {
    vector<Block::Point> test = createSearchVector(x, y);
    vector<Block> blockSum = vector<Block>();

    //find initial disp
    for (auto &iter : test) {
        double sum = MSE_FUNCTIONS::compute_mse(*this->input_image, *this->desired_image,
                                                iter.x, iter.y,
                                                x, y,
                                                block_size);
        Block b = Block(iter.x, iter.y, x, y, sum);
        blockSum.push_back(b);
    }

    auto smallestBlock = std::min_element(blockSum.begin(), blockSum.end());
    return *smallestBlock;
}
