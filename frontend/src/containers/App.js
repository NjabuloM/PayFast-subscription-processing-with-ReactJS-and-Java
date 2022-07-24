import React, { Component } from 'react';
import '../assets/css/App.css';
import DisplayCabinet from '../components/DisplayCabinet';

import { connect } from "react-redux";
import { browseNewsStand } from '../redux/catalogue/reducer';
import { payForSubscription } from "../redux/subscription/reducer";

class Application extends Component {

    componentDidMount() {
        //Make the API call
        const newsstand = this.props.newsstand;
        if (newsstand === undefined) {
            this.props.browseNewsStand();
        }
    }
    
    render() {
        return (
            <div className="App">
                <h1>Spaza Shop: Magazine Stand</h1>
                <DisplayCabinet />
            </div>
        );
    }
}

const mapStateToProps = ({ message, formData, newsstand }) => {
    return {
        message,
        formData,
        newsstand
    }
}

export default connect(mapStateToProps, {payForSubscription, browseNewsStand})(Application);