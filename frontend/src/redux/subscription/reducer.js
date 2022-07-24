import { createSlice } from "@reduxjs/toolkit";
const initialState = { formData: {}, selectedMagazineId: 0, loading: false, magazine: {} }

const subscribeSlice = createSlice({
    name: 'subscribe',
    initialState,
    reducers: {
        selectSubscription: (state, action) => {
            state.selectedMagazineId = action.payload;
        },
        viewMagazineDetails: (state, action) => {
            state.loading = true;
        },
        receiveMagazineDetails: (state, action) => {
            state.magazine = action.payload;
        },
        payForSubscription: (state, action) => {
        },
        receiveFormData: (state, action) => {
            state.formData = action.payload.formData;
        }
    }
})

export const { selectSubscription, viewMagazineDetails, payForSubscription, receiveFormData, receiveMagazineDetails } = subscribeSlice.actions
export default subscribeSlice.reducer