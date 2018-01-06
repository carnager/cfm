package Cfm::Ui::Wizard::FixPlaybackWizard;
use strict;
use warnings FATAL => 'all';
use Log::Any qw/$log/;
use Moo;
with 'Cfm::Singleton';

use Cfm::Autowire;
use Cfm::Mb::MbService;
use Cfm::Playback::PlaybackService;
use Cfm::Ui::Selector::Selector;
use Term::Form;

has form => (is => 'ro', default => sub {Term::Form->new('name')});
has formatter => autowire(
        'Cfm::Ui::Format::Pretty' => 'Cfm::Ui::Format::Formatter',
        row_count                 => 1,
    );
has mbservice => singleton 'Cfm::Mb::MbService';
has playback_service => singleton 'Cfm::Playback::PlaybackService';

sub fix_acc_playback {
    my ($self, $acc) = @_;

    $self->formatter->accumulated_playback($acc);
    my $artists = $self->determine_artists($acc->artists);
    die $log->fatal("No artists to lookup") unless scalar $artists->@* > 0;
    my $rg_id = $self->determine_rg_id($artists, $acc->releaseTitle);
    die $log->fatal("No release group id found") unless defined $rg_id;
    my $rec_id = $self->determine_rec_id($rg_id, $acc->recordingTitle);
    die $log->fatal("No recording id found") unless defined $rec_id;

    $acc->releaseGroupId($rg_id);
    $acc->recordingId($rec_id);
    $self->playback_service->fix_acc_playback($acc);
}

sub determine_artists {
    my ($self, $artist_names) = @_;

    my @consolidated;
    map {
        my $ca = $self->_prompt("> ", $_);
        push @consolidated, $ca if $ca =~ /\S+/;
    } $artist_names->@*;
    \@consolidated;
}

sub determine_rg_id {
    my ($self, $artists, $release_title) = @_;

    my $selected_rg = Cfm::Ui::Selector::Selector::numerical_select(
        sub {$self->mbservice->identify_release_group($artists, $release_title, $_[0])},
        sub {$self->formatter->release_groups($_[0])},
    );
    die $log->fatal("No release group selected.") unless defined $selected_rg;
    $selected_rg->id;
}

sub determine_rec_id {
    my ($self, $rg_id, $recording_title) = @_;

    my $selected_rec = Cfm::Ui::Selector::Selector::numerical_select(
        sub {$self->mbservice->identify_recording($rg_id, $recording_title, $_[0])},
        sub {$self->formatter->recordings($_[0])},
    );
    die $log->fatal("No recording selected.") unless defined $selected_rec;
    $selected_rec->id;
}

sub _prompt {
    my ($self, $prompt, $default) = @_;

    $self->form->readline($prompt, {default => $default});
}

1;
