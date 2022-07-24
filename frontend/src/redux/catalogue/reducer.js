import { createSlice } from "@reduxjs/toolkit";

const initialState = { newsstand: [], loading: false }
const catalogueSlice = createSlice({
    name: 'catalogue',
    initialState,
    reducers: {
        browseNewsStand: (state) => {
            state.loading = true;
        },
        showMagazineStand: (state, action) => {
            state.newsstand = action.payload;
            state.loading = false;
        }
    }
})

export const { browseNewsStand, showMagazineStand } = catalogueSlice.actions
export default catalogueSlice.reducer